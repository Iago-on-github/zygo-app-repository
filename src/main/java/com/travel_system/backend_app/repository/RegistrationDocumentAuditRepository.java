package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.RegistrationDocumentAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegistrationDocumentAuditRepository extends JpaRepository<RegistrationDocumentAudit, UUID> {

    @Modifying
    @Query("DELETE FROM RegistrationDocumentAudit rda WHERE rda.registrationRequestId IN :requestIds")
    void deleteAllByRegistrationRequestId(List<UUID> requestIds);
}
