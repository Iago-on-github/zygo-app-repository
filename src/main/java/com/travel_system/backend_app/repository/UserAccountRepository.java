package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    UserAccount findUserByEmail(String email);

    @Query(value = "SELECT * FROM user_account_table u WHERE u.email = :email", nativeQuery = true)
    Optional<UserAccount> findByEmailForAuthentication(@Param("email") String email);

/*
    @Query("SELECT u FROM UserAccount u JOIN u.permissions p WHERE u.email = :email AND p.description = :role")
    Optional<UserAccount> findByEmailAndRole(@Param("email") String email, @Param("role") String role);
*/

    boolean existsByEmail(@Param("email") String email);
}
