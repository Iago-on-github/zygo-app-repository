package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.time.Instant;
import java.util.UUID;

public record RegistrationRejectionDTO(
        UUID requestId,
        RegistrationRequestType type,
        String rejectionReason,
        Instant reviewedAt
) {
}
