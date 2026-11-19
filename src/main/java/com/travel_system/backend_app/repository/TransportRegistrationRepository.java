package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;
import com.travel_system.backend_app.projections.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransportRegistrationRepository extends JpaRepository<TransportRegistration, UUID> {

    @Query("SELECT tr FROM TransportRegistration tr WHERE tr.studentId = :ownerId OR tr.driverId = :ownerId")
    Optional<TransportRegistration> findByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT tr FROM TransportRegistration tr WHERE (tr.studentId = :ownerId OR tr.driverId = :ownerId) AND tr.status <> :status")
    Optional<TransportRegistration> findCurrentByOwnerId(@Param("ownerId") UUID ownerId, @Param("status")TransportRegistrationStatus status);

    // matrículas do titular em um status, da mais recente para a mais antiga (usado com PageRequest.of(0, 1))
    @Query("""
        SELECT r FROM TransportRegistration r
        WHERE (r.studentId = :ownerId OR r.driverId = :ownerId)
          AND r.status = :status
        ORDER BY r.closedAt DESC
        """)
    List<TransportRegistration> findLatestByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") TransportRegistrationStatus status);

    @Query("SELECT r FROM TransportRegistration r JOIN FETCH r.customerTerm WHERE r.status IN :statuses AND r.customerId = :customerId")
    List<TransportRegistration> findWithTermByCustomerIdAndStatuses(@Param("customerId") UUID customerId, @Param("statuses") List<TransportRegistrationStatus> statuses );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM TransportRegistration r JOIN FETCH r.customerTerm WHERE r.id = :id")
    Optional<TransportRegistration> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT r FROM TransportRegistration r WHERE r.studentId = :ownerId OR r.driverId = :ownerId")
    List<TransportRegistration> findAllByOwnerId(@Param("ownerId") UUID ownerId);

    // contagem de matrículas por status
    @Query("""
        SELECT r.status AS status, COUNT(r) AS total
        FROM TransportRegistration r
        WHERE r.customerId = :customerId
        GROUP BY r.status
        """)
    List<StatusCountView> countGroupedByStatus(@Param("customerId") UUID customerId);

    /*
     * matrículas ACTIVE por faixa do fim do período, divididas por tipo de titular
     * - em dia: endTerm >= today
     * - em carência: graceLimit <= endTerm < today
     * - aguardando encerramento: endTerm < graceLimit
     * */
    @Query("""
        SELECT
            SUM(CASE WHEN t.endTerm >= :today AND r.studentId IS NOT NULL THEN 1 ELSE 0 END) AS activeStudents,
            SUM(CASE WHEN t.endTerm >= :today AND r.driverId  IS NOT NULL THEN 1 ELSE 0 END) AS activeDrivers,
            SUM(CASE WHEN t.endTerm < :today AND t.endTerm >= :graceLimit AND r.studentId IS NOT NULL THEN 1 ELSE 0 END) AS graceStudents,
            SUM(CASE WHEN t.endTerm < :today AND t.endTerm >= :graceLimit AND r.driverId  IS NOT NULL THEN 1 ELSE 0 END) AS graceDrivers,
            SUM(CASE WHEN t.endTerm < :graceLimit THEN 1 ELSE 0 END) AS awaitingClosure
        FROM TransportRegistration r
        JOIN r.customerTerm t
        WHERE r.customerId = :customerId
          AND r.status = :active
        """)
    ActiveRangeCountView countActiveByTermEndRange(@Param("customerId") UUID customerId,
                                                   @Param("today") LocalDate today,
                                                   @Param("graceLimit") LocalDate graceLimit,
                                                   @Param("active") TransportRegistrationStatus active);

    /*
     * andamento da renovação- matrículas ACTIVE com a janela aberta e ainda dentro da carência
     * - o LEFT JOIN traz a renovação aberta (se houver)
     * - se rr.id nulo = ainda não começou a renovar
     * */
    @Query("""
        SELECT
            SUM(CASE WHEN rr.id IS NULL THEN 1 ELSE 0 END) AS notStarted,
            SUM(CASE WHEN rr.status = :draft THEN 1 ELSE 0 END) AS inDraft,
            SUM(CASE WHEN rr.status = :underReview THEN 1 ELSE 0 END) AS underReview
        FROM TransportRegistration r
        JOIN r.customerTerm t
        LEFT JOIN RegistrationRequest rr
               ON rr.transportRegistration = r
              AND rr.type = :renewal
              AND rr.status IN (:draft, :underReview)
        WHERE r.customerId = :customerId
          AND r.status = :active
          AND t.endTerm <= :windowLimit
          AND t.endTerm >= :graceLimit
        """)
    RenewalProgressView countRenewalProgress(@Param("customerId") UUID customerId,
                                             @Param("windowLimit") LocalDate windowLimit,
                                             @Param("graceLimit") LocalDate graceLimit,
                                             @Param("active") TransportRegistrationStatus active,
                                             @Param("renewal") RegistrationRequestType renewal,
                                             @Param("draft") RegistrationRequestStatus draft,
                                             @Param("underReview") RegistrationRequestStatus underReview);


    // datas de fim distintas (com a quantidade) das matrículas com a janela de renovação aberta
    @Query("""
        SELECT t.endTerm AS endTerm, COUNT(r) AS total
        FROM TransportRegistration r
        JOIN r.customerTerm t
        WHERE r.customerId = :customerId
          AND r.status = :active
          AND t.endTerm <= :windowLimit
          AND t.endTerm >= :graceLimit
        GROUP BY t.endTerm
        """)
    List<TermEndCountView> countOpenWindowGroupedByTermEnd(@Param("customerId") UUID customerId,
                                                           @Param("windowLimit") LocalDate windowLimit,
                                                           @Param("graceLimit") LocalDate graceLimit,
                                                           @Param("active") TransportRegistrationStatus active);

    /*
     * matrículas ACTIVE em carência (período terminado, carência não esgotada)
     * - blockingSoonLimit restringe ao prazo do aviso de bloqueio; sem o filtro, recebe "today" (não restringe nada a mais)
     * - ordem fixa: quem perde o acesso primeiro aparece no topo
     * */
    @Query(value = """
        SELECT r.id AS registrationId, r.studentId AS studentId, r.driverId AS driverId,
               r.registrationNumber AS registrationNumber, t.endTerm AS termEnd
        FROM TransportRegistration r
        JOIN r.customerTerm t
        WHERE r.customerId = :customerId
          AND r.status = :active
          AND t.endTerm >= :graceLimit
          AND t.endTerm < :today
          AND t.endTerm <= :blockingSoonLimit
        ORDER BY t.endTerm ASC
        """,
            countQuery = """
        SELECT COUNT(r)
        FROM TransportRegistration r
        JOIN r.customerTerm t
        WHERE r.customerId = :customerId
          AND r.status = :active
          AND t.endTerm >= :graceLimit
          AND t.endTerm < :today
          AND t.endTerm <= :blockingSoonLimit
        """)
    Page<GraceRegistrationView> findInGrace(@Param("customerId") UUID customerId,
                                            @Param("active") TransportRegistrationStatus active,
                                            @Param("graceLimit") LocalDate graceLimit,
                                            @Param("today") LocalDate today,
                                            @Param("blockingSoonLimit") LocalDate blockingSoonLimit,
                                            Pageable pageable);


    @Query("""
        SELECT COALESCE(r.studentId, r.driverId) AS ownerId,
               r.status AS status,
               t.endTerm AS termEnd,
               CASE WHEN EXISTS (
                   SELECT 1 FROM CustomerTerm nt
                   WHERE nt.customerId = r.customerId
                     AND nt.startTerm > t.endTerm
               ) THEN true ELSE false END AS hasNextTerm,
               o.type AS openRequestType,
               o.status AS openRequestStatus,
               (SELECT MAX(rj.reviewedAt) FROM RegistrationRequest rj
                WHERE rj.transportRegistration = r
                  AND rj.type = :renewal
                  AND rj.status = :rejected) AS lastRenewalRejectedAt
        FROM TransportRegistration r
        LEFT JOIN r.customerTerm t
        LEFT JOIN RegistrationRequest o
               ON o.transportRegistration = r AND o.status IN :openStatuses
        WHERE r.customerId = :customerId
          AND r.status <> :closed
          AND ((:student = true AND r.studentId IN :ownerIds)
               OR (:student = false AND r.driverId IN :ownerIds))
        """)
    List<RegistrationStatusView> findStatusViews(@Param("customerId") UUID customerId,
                                                 @Param("student") boolean student,
                                                 @Param("ownerIds") Collection<UUID> ownerIds,
                                                 @Param("openStatuses") Collection<RegistrationRequestStatus> openStatuses,
                                                 @Param("renewal") RegistrationRequestType renewal,
                                                 @Param("rejected") RegistrationRequestStatus rejected,
                                                 @Param("closed") TransportRegistrationStatus closed);

    default List<RegistrationStatusView> findStatusViews(UUID customerId, OwnerType ownerType, Collection<UUID> ownerIds) {
        return findStatusViews(
                customerId,
                ownerType == OwnerType.STUDENT,
                ownerIds,
                List.of(RegistrationRequestStatus.DRAFT, RegistrationRequestStatus.UNDER_REVIEW),
                RegistrationRequestType.RENEWAL,
                RegistrationRequestStatus.REJECTED,
                TransportRegistrationStatus.CLOSED
        );
    }

    @Query("""
        SELECT DISTINCT r.studentId FROM TransportRegistration r
        WHERE r.customerId = :customerId AND r.studentId IN :ownerIds AND r.status = :closed
        """)
    List<UUID> findClosedStudentIds(@Param("customerId") UUID customerId,
                                    @Param("ownerIds") Collection<UUID> ownerIds,
                                    @Param("closed") TransportRegistrationStatus closed);

    @Query("""
        SELECT DISTINCT r.driverId FROM TransportRegistration r
        WHERE r.customerId = :customerId AND r.driverId IN :ownerIds AND r.status = :closed
        """)
    List<UUID> findClosedDriverIds(@Param("customerId") UUID customerId,
                                   @Param("ownerIds") Collection<UUID> ownerIds,
                                   @Param("closed") TransportRegistrationStatus closed);

    default List<UUID> findClosedOwnerIds(UUID customerId, OwnerType ownerType, Collection<UUID> ownerIds) {
        return ownerType == OwnerType.STUDENT ? findClosedStudentIds(customerId, ownerIds, TransportRegistrationStatus.CLOSED) : findClosedDriverIds(customerId, ownerIds, TransportRegistrationStatus.CLOSED);
    }

    @Modifying
    @Query("""
        UPDATE TransportRegistration tr
            SET tr.status = :activeStatus, tr.version = tr.version + 1, tr.inactiveAt = NULL
        WHERE (tr.studentId = :ownerId OR tr.driverId = :ownerId)
            AND tr.status = :inactiveStatus
        """)
    int reactivateByOwnerId(@Param("ownerId") UUID ownerId, @Param("activeStatus") TransportRegistrationStatus activeStatus, @Param("inactiveStatus") TransportRegistrationStatus inactiveStatus);

    @Query("""
        SELECT r.customerId FROM TransportRegistration r
        WHERE (r.studentId = :ownerId OR r.driverId = :ownerId)
          AND r.status <> :closed
        """)
    UUID findCustomerIdByOwnerId(@Param("ownerId") UUID ownerId, @Param("closed") TransportRegistrationStatus closed);
}
