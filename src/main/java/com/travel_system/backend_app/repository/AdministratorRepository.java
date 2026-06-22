package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdministratorRepository extends JpaRepository<Administrator, UUID> {
    List<Administrator> findByStatus(GeneralStatus generalStatus);

    Optional<Administrator> findByEmail(String email);

    Optional<Administrator> findByTelephone(String telephone);

    Optional<Administrator> findByEmailOrTelephoneAndIdNot(String email, String telephone, UUID id);

    @Query("SELECT a FROM Administrator a WHERE a.customer IS NOT NULL")
    List<Administrator> findAllWithCustomerId();

    @Query("SELECT a FROM Administrator a WHERE a.status = :status AND a.customer IS NOT NULL")
    List<Administrator> findByStatusWithCustomerId(GeneralStatus status);
}
