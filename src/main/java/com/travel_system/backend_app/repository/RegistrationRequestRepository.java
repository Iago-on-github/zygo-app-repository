package com.travel_system.backend_app.repository;

import com.google.common.collect.Collections2;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;
import com.travel_system.backend_app.projections.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    @Query("SELECT r FROM RegistrationRequest r WHERE r.transportRegistration.id = :registrationId AND r.status IN :statuses")
    Optional<RegistrationRequest> findOpenByRegistrationId(@Param("registrationId") UUID registrationId, @Param("statuses") Collection<RegistrationRequestStatus> statuses);

    // solicitação mais recente da matrícula
    Optional<RegistrationRequest> findFirstByTransportRegistration_IdOrderByCreatedAtDesc(UUID registrationId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM RegistrationRequest r
        WHERE r.transportRegistration.id = :registrationId
          AND r.status IN :statuses
        """)
    Optional<RegistrationRequest> findOpenByRegistrationIdForUpdate(@Param("registrationId") UUID registrationId,
                                                                    @Param("statuses") Collection<RegistrationRequestStatus> statuses);

    @Query("""
        SELECT r.id FROM RegistrationRequest r
        WHERE r.customerId = :customerId
          AND r.status = :draft
          AND r.type <> :initial
          AND r.updatedAt < :cutoff
          AND NOT EXISTS (
              SELECT 1 FROM Document d
              WHERE d.registrationRequestId = r.id
                AND d.createdAt >= :cutoff
          )
        """)
    List<UUID> findAbandonedDraftIds(@Param("customerId") UUID customerId,
                                     @Param("draft") RegistrationRequestStatus draft,
                                     @Param("initial") RegistrationRequestType initial,
                                     @Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RegistrationRequest r WHERE r.id = :id")
    Optional<RegistrationRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
        SELECT r
        FROM RegistrationRequest r
        JOIN FETCH r.transportRegistration
        WHERE r.status = :status
        ORDER BY r.submittedAt ASC
        """,
            countQuery = """
        SELECT COUNT(r)
        FROM RegistrationRequest r
        WHERE r.status = :status
        """
    )
    Page<RegistrationRequest> findAllToReview(
            @Param("status") RegistrationRequestStatus status,
            Pageable pageable
    );

    @Query("SELECT r FROM RegistrationRequest r WHERE r.transportRegistration IN :activeRegistrationsIds AND r.status IN :openStatuses")
    List<RegistrationRequest> findOpenByRegistrationIds(@Param("activeRegistrationsIds") List<UUID> activeRegistrationsIds, @Param("openStatuses") List<RegistrationRequestStatus> openStatuses);

    @Query("SELECT r FROM RegistrationRequest r WHERE r.transportRegistration.studentId = :ownerId OR r.transportRegistration.driverId = :ownerId")
    List<RegistrationRequest> findAllByOwnerId(UUID ownerId);

    // fila de análise agrupada por tipo, com o envio mais antigo de cada tipo
    @Query("""
        SELECT r.type AS type, COUNT(r) AS total, MIN(r.submittedAt) AS oldestSubmittedAt
        FROM RegistrationRequest r
        WHERE r.customerId = :customerId
          AND r.status = :underReview
        GROUP BY r.type
        """)
    List<ReviewQueueCountView> countUnderReviewGroupedByType(@Param("customerId") UUID customerId, @Param("underReview") RegistrationRequestStatus underReview);

    @Query("""
    SELECT
        COUNT(DISTINCT rr.studentId) AS students,
        COUNT(DISTINCT rr.driverId) AS drivers
    FROM RegistrationRequest r
    JOIN r.transportRegistration rr
    JOIN r.targetTerm ct
    WHERE
        (rr.studentId IS NOT NULL OR rr.driverId IS NOT NULL)
        AND rr.customerId = :customerId
        AND ct.id = :termId
        AND r.status = :status
        AND r.type IN :types
    """)
    DistributionTotalsView countApprovedStudentsByTermAndGroupedByType(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);

    @Query("""
    SELECT 
        i.id AS institutionId,
        i.institutionName AS institutionName,
        i.institutionType AS institutionType,
        
        ic.id AS courseId,
        ic.name AS courseName,
        
        SUM (CASE WHEN i.id IS NOT NULL AND ic.id IS NOT NULL THEN 1 ELSE 0 END) AS total
    FROM RegistrationRequest rr
    LEFT JOIN InstitutionCourse ic ON ic.id = rr.institutionCourseId
    LEFT JOIN ic.institution i
    WHERE rr.customerId = :customerId
        AND rr.targetTerm.id = :termId
        AND rr.status = :status
        AND rr.type IN :types
    GROUP BY i.id, i.institutionName, ic.id, ic.name
""")
    List<InstitutionCourseCountView> getInstitutionsAndCoursesGroupedByType(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);

    @Query("""
    SELECT s AS shifts, COUNT (DISTINCT r.studentId) AS total
    FROM RegistrationRequest rr
        JOIN rr.transportRegistration r
        JOIN rr.shifts s
    WHERE r.customerId = :customerId 
        AND r.customerTerm.id = :termId
        AND rr.status = :status
        AND rr.type IN :types 
    GROUP BY s
""")
    List<ShiftCountView> getAndCountStudentShifts(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);


    @Query("""
    SELECT i.id AS institutionId, i.institutionName AS institutionName, s AS shifts,
        COUNT (DISTINCT i.id) AS total
    FROM RegistrationRequest rr 
        JOIN rr.transportRegistration r
        JOIN rr.shifts s
        LEFT JOIN InstitutionCourse ic ON ic.id = rr.institutionCourseId 
        LEFT JOIN ic.institution i
    WHERE rr.customerId = :customerId
        AND rr.targetTerm.id = :termId
        AND rr.status = :status
        AND rr.type IN :types
    GROUP BY i.id, i.institutionName, s
""")
    List<InstitutionShiftCountView> institutionShiftCount(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);

    @Query("""
        SELECT COUNT(DISTINCT r.id) 
                FROM RegistrationRequest rr
                JOIN rr.transportRegistration r
                WHERE rr.customerId = :customerId
                    AND rr.status = :status
                    AND rr.type IN :types
                    AND r.studentId IS NOT NULL
                    AND NOT EXISTS (
                            SELECT 1 FROM StudentRouteStopAssignment a 
                            WHERE a.student.id = r.studentId
                                AND a.standardRoute IS NOT NULL)
                """)
    Long countStudentsWithoutAnyRoute(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);


    @Query("""
            SELECT sr.id AS standardRouteId, sr.routeName AS standardRouteName, COUNT(DISTINCT r.id) AS total
            FROM RegistrationRequest rr
            JOIN rr.transportRegistration r
            JOIN StudentRouteStopAssignment a ON a.student.id = r.studentId
            JOIN a.standardRoute sr
            WHERE rr.customerId = :customerId
              AND rr.targetTerm.id = :termId
              AND rr.status = :status
              AND rr.type IN :types
              AND r.studentId IS NOT NULL
            GROUP BY sr.id, sr.routeName
            ORDER BY COUNT(DISTINCT r.id) DESC
                """)
    List<RouteCountView> studentsPerPeriodAndRoute(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);

    @Query("""
            SELECT sr.id AS standardRouteId, sr.routeName AS standardRouteName, a.travelPeriod AS travelPeriod, a.travelDirection AS travelDirection, COUNT(DISTINCT r.id) AS total
            FROM RegistrationRequest rr
            JOIN rr.transportRegistration r
            JOIN StudentRouteStopAssignment a ON a.student.id = r.studentId
            JOIN a.standardRoute sr
            WHERE rr.customerId = :customerId
              AND rr.targetTerm.id = :termId
              AND rr.status = :status
              AND rr.type IN :types
              AND r.studentId IS NOT NULL
            GROUP BY sr.id, sr.routeName, a.travelPeriod, a.travelDirection
            ORDER BY COUNT(DISTINCT r.id) DESC
                """)
    List<RouteScheduleCountView> studentsDetailsPerPeriodTrip(
            @Param("customerId") UUID customerId,
            @Param("termId") UUID termId,
            @Param("status") RegistrationRequestStatus status,
            @Param("types") List<RegistrationRequestType> types);

    /*
     * matrículas PENDING paradas: a ÚLTIMA solicitação está em DRAFT sem atividade, ou foi rejeitada sem reenvio
     * - since: updatedAt do rascunho ou reviewedAt da rejeição
     * - NOT EXISTS: garante que é a solicitação mais recente da matrícula
     * */
    @Query(value = """
        SELECT r.id AS registrationId, r.studentId AS studentId, r.driverId AS driverId,
               rr.id AS requestId, rr.status AS requestStatus,
               CASE WHEN rr.status = :draft THEN rr.updatedAt ELSE rr.reviewedAt END AS since
        FROM RegistrationRequest rr
        JOIN rr.transportRegistration r
        WHERE r.customerId = :customerId
          AND r.status = :pending
          AND ((rr.status = :draft AND rr.updatedAt < :cutoff)
            OR (rr.status = :rejected AND rr.reviewedAt < :cutoff))
          AND NOT EXISTS (
              SELECT 1 FROM RegistrationRequest newer
              WHERE newer.transportRegistration = r
                AND newer.createdAt > rr.createdAt
          )
        ORDER BY CASE WHEN rr.status = :draft THEN rr.updatedAt ELSE rr.reviewedAt END ASC
        """,
            countQuery = """
        SELECT COUNT(rr)
        FROM RegistrationRequest rr
        JOIN rr.transportRegistration r
        WHERE r.customerId = :customerId
          AND r.status = :pending
          AND ((rr.status = :draft AND rr.updatedAt < :cutoff)
            OR (rr.status = :rejected AND rr.reviewedAt < :cutoff))
          AND NOT EXISTS (
              SELECT 1 FROM RegistrationRequest newer
              WHERE newer.transportRegistration = r
                AND newer.createdAt > rr.createdAt
          )
        """)
    Page<StalledRegistrationView> findStalled(@Param("customerId") UUID customerId,
                                              @Param("pending") TransportRegistrationStatus pending,
                                              @Param("draft") RegistrationRequestStatus draft,
                                              @Param("rejected") RegistrationRequestStatus rejected,
                                              @Param("cutoff") Instant cutoff,
                                              Pageable pageable);}
