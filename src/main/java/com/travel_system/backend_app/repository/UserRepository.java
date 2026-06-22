package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserModel, UUID> {
    UserModel findUserByEmail(String email);

    @Query("SELECT n FROM UserModel n WHERE n.name = :name AND n.status = 'ACTIVE' ")
    Set<String> findByName(String name);

    boolean existsByEmailAndIdAndStatus(String email, UUID id, GeneralStatus status);

    Optional<UserModel> findByEmailAndCustomerId(String email, UUID customerId);

    @Query("SELECT u FROM UserModel u JOIN u.permissions p WHERE u.email = :email AND p.description =: role")
    Optional<UserModel> findByEmailAndRole(@Param("email") String email, @Param("role") String role);
}
