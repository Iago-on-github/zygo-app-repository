package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.SensitiveOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SensitiveOperationRepository extends JpaRepository<SensitiveOperation, UUID> {
    Optional<SensitiveOperation> findByRequestedByUserAccountEmail(@Param("authenticatedUserEmail") String authenticatedUserEmail);

    Optional<SensitiveOperation> findByVerificationTokenHash(@Param("hash") String hash);
}
