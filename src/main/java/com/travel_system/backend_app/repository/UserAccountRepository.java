package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    UserAccount findUserByEmail(String email);

    @Query(value = "SELECT * FROM user_account_table u WHERE u.email = :email", nativeQuery = true)
    Optional<UserAccount> findByEmailForAuthentication(@Param("email") String email);

    /*
    * useraccount nao tem customerId, o nome "ignoringTenant" serve apenas para deixar mais explicíto, mas nao é nativequery
    * */
    @Query("SELECT COUNT (u) > 0 FROM UserAccount u WHERE u.id = :userAccountId")
    boolean existsByUserAccountIdIgnoringTenant(@Param("userAccountId") UUID userAccountId);

    boolean existsByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // impede que outras transações leiam, atualizem ou excluam os dados bloqueados até que a transação atual seja confirmada ou rolada
    @Query("SELECT u FROM UserAccount u WHERE u.email = :email")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);

    @Modifying
    @Query("UPDATE UserAccount u SET u.lastActivityAt = :lastActivityAt WHERE u.id = :userAccountId AND u.lastActivityAt IS NULL " +
            "OR u.lastActivityAt < :twentyFourHoursAgo")
    int recordUserLastActivity(@Param("userAccountId") UUID userAccountId, @Param("lastActivityAt") Instant lastActivityAt, @Param("twentyFourHoursAgo") Instant twentyFourHoursAgo);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM UserAccount u WHERE u.id = :userAccountId AND u.inactiveAt IS NOT NULL")
    boolean existsInactiveUserById(@Param("userAccountId") UUID userAccountId);

    @Modifying
    @Query("UPDATE UserAccount u SET u.inactiveAt = NULL WHERE u.inactiveAt IS NOT NULL AND u.id = :userAccountId")
    int clearInactiveAtByUserId(@Param("userAccountId") UUID userAccountId);

    @Query("SELECT u.userAccountType FROM UserAccount u WHERE u.id = :userAccountId")
    UserAccountType findUserTypeByUserId(UUID userAccountId, Collection<UserAccountType> types);
}
