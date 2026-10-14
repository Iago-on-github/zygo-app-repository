package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.PendingDocsExclusions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingDocsExclusionsRepository extends JpaRepository<PendingDocsExclusions, UUID> {
    Optional<PendingDocsExclusions> findByStorageKey(String first);

    List<PendingDocsExclusions> findAllByAttemptsLessThan(int maxAttempts);
}
