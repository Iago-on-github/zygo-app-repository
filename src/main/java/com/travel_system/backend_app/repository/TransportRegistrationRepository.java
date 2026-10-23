package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
