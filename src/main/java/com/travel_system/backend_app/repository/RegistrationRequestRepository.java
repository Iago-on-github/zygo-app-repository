package com.travel_system.backend_app.repository;

import com.google.common.collect.Collections2;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.model.TransportRegistration;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    @Query("SELECT r FROM RegistrationRequest WHERE transportRegistration.id = :registrationId AND r.status IN :statuses")
    Optional<RegistrationRequest> findOpenByRegistrationId(@Param("registrationId") UUID registrationId, @Param("statuses") Collection<RegistrationRequestStatus> statuses);

    @Query("SELECT r FROM RegistrationRequest WHERE r.transportRegistration.id = :registrationId ORDER BY r.createdAt DESC")
    Optional<RegistrationRequest> findLastByRegistrationId(@Param("registrationId") UUID registrationId);

    // solicitação mais recente da matrícula
    Optional<RegistrationRequest> findFirstByTransportRegistration_IdOrderByCreatedAtDesc(UUID registrationId);}
