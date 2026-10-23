package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.Shift;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record RegistrationRequestSummaryDTO(
        UUID id,
        RegistrationRequestType type,
        RegistrationRequestStatus status,

        // dados já preenchidos (null se ainda não foram)
        String identityNumber,
        String identityIssuer,
        LocalDate identityIssuedAt,
        String motherName,
        String fatherName,

        // dados da instituição do estudante para exibir no front
        UUID institutionCourseId,
        String poolOfEnrollment,
        Set<Shift> shifts,

        String rejectionReason, // exibido apenas se o ultimo status fo rejected
        Instant submittedAt
) {
}
