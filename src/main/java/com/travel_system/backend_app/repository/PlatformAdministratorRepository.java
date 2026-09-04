package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.PlatformAdministrator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformAdministratorRepository extends JpaRepository<PlatformAdministrator, UUID> {

    @Query("SELECT pa FROM PlatformAdministrator pa WHERE pa.userAccount.email = :email")
    Optional<PlatformAdministrator> findByEmail(@Param("email") String email);

}
