package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TransportRegistrationReviewDetailsDTO(
        // solicitação
        UUID id,
        RegistrationRequestType type,
        RegistrationRequestStatus requestStatus,
        Instant submittedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,

        // dados preenchidos
        String identityNumber,
        String identityIssuer,
        LocalDate identityIssuedAt,
        String motherName,
        String fatherName,
        UUID institutionCourseId,
        String poolOfEnrollment,
        Set<Shift> shifts,

        // documentos
        List<RegistrationReqDocuments> documents,

        boolean hasPendingPhoto,
        boolean hasRegistrationPhoto,

        // matricula
        String registrationNumber,
        TransportRegistrationStatus registrationStatus,
        LocalDate endTerm,

        // titular
        String name,
        OwnerType ownerType


) {
    public record RegistrationReqDocuments(
            UUID documentId,
            DocumentType documentType,
            Instant createdAt
    ) {}
}
