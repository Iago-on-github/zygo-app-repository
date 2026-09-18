package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Permissions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionsRepository extends JpaRepository<Permissions, UUID> {
    Optional<Permissions> findByDescription(String permission);
}
