package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByUserAccountId(@Param("userAccountId") UUID userAccountId);

    Optional<EmailVerificationToken> findByTokenHash(@Param("calculatedSimpleHash") String calculatedSimpleHash);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE EmailVerificationToken t
        SET t.usedAt = :now
        WHERE t.userAccountId = :userAccountId AND t.usedAt IS NULL
        """)
    int invalidatePendingTokens(@Param("userAccountId") UUID userAccountId, @Param("now") Instant now);
}
