package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SensitiveOperationRepository extends JpaRepository<SensitiveOperation, UUID> {
    Optional<SensitiveOperation> findByRequestedByUserAccountEmail(@Param("authenticatedUserEmail") String authenticatedUserEmail);

    Optional<SensitiveOperation> findByVerificationTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SensitiveOperation s WHERE s.verificationTokenHash = :hash")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")) // impede que uma segunda requisição fique waiting por tempo indeterminado se a primeira falhar
    Optional<SensitiveOperation> findByVerificationTokenHashForUpdate(@Param("hash") String hash);

    /*
     *  marca em lote como EXPIRED as operações vencidas:
     * - só altera operações ainda abertas (PENDING ou APPROVED), nunca um estado final
     * - limpa o payload, que pode conter dados sensíveis (ex.: hash de senha)
     * - retorna a quantidade de operações afetadas
     * */
    @Modifying
    @Query("""
        UPDATE SensitiveOperation so
           SET so.sensitiveOperationStatus = :expiredStatus,
               so.payload = null
         WHERE so.sensitiveOperationStatus IN :openStatuses
           AND so.expiresAt < :now
        """)
    int expireOverdue(
            @Param("expiredStatus") SensitiveOperationStatus expiredStatus,
            @Param("openStatuses") Collection<SensitiveOperationStatus> openStatuses,
            @Param("now") Instant now);
}
