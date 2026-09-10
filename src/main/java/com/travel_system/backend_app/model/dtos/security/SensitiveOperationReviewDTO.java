package com.travel_system.backend_app.model.dtos.security;

import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import java.time.Instant;
import java.util.UUID;

public record SensitiveOperationReviewDTO(
        UUID id,
        SensitiveOperationType sensitiveOperationType,
        String requestedByUserAccountEmail,
        Instant expiresAt
) {
}
