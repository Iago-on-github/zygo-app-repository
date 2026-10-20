package com.travel_system.backend_app.repository;

import com.fasterxml.jackson.databind.introspect.AnnotationCollector;
import com.travel_system.backend_app.model.HealthConsent;
import com.travel_system.backend_app.model.dtos.response.HealthConsentResponseDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthConsentRepository extends JpaRepository<HealthConsent, UUID> {

    @Query("SELECT hs FROM HealthConsent hs WHERE hs.studentId = :ownerId OR hs.driverId = :ownerId AND hs.revokedAt IS NULL")
    Optional<HealthConsent> findByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("""
        SELECT hc FROM HealthConsent hc
        WHERE (hc.studentId = :ownerId OR hc.driverId = :ownerId)
          AND hc.revokedAt IS NULL
        """)
    Optional<HealthConsent> findActiveByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT hc FROM HealthConsent hc WHERE hc.studentId = :ownerId OR hc.driverId = :ownerId")
    List<HealthConsent> findAllByOwnerId(@Param("ownerId") UUID ownerId);
}
