package com.travel_system.backend_app.repository;

import com.google.common.collect.Collections2;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
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
}
