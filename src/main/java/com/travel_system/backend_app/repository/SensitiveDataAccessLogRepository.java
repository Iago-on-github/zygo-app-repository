package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.interfaces.mappers.response.CustomerResponseMapper;
import com.travel_system.backend_app.model.SensitiveDataAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SensitiveDataAccessLogRepository extends JpaRepository<SensitiveDataAccessLog, UUID> {
    Optional<SensitiveDataAccessLog> findByOwnerId(UUID ownerId);

    @Query("""
        SELECT l FROM SensitiveDataAccessLog l
        WHERE (:ownerId IS NULL OR l.ownerId = :ownerId)
          AND (:actorAccountId IS NULL OR l.actorAccountId = :actorAccountId)
          AND (:from IS NULL OR l.accessAt >= :from)
          AND (:to IS NULL OR l.accessAt < :to)
        """)
    Page<SensitiveDataAccessLog> search(@Param("ownerId") UUID ownerId,
                                        @Param("actorAccountId") UUID actorAccountId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);}
