package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByEmail(String email);

    Optional<Driver> findByTelephone(String telephone);

    Optional<Driver> findByEmailOrTelephoneAndIdNot(String email, String telephone, UUID id);

    Optional<Driver> findByEmailOrTelephone(String email, String telephone);

    Page<Driver> findAllByStatus(GeneralStatus status, Pageable pageable);
}
