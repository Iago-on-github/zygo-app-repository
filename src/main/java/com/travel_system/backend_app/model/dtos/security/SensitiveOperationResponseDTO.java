package com.travel_system.backend_app.model.dtos.security;

import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;

import java.time.Instant;
import java.util.UUID;

public record SensitiveOperationResponseDTO (
        UUID id,
        SensitiveOperationStatus sensitiveOperationStatus,
        String message,
        Instant expiresAt
){
}
