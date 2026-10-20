package com.travel_system.backend_app.model.dtos.response;

import java.time.Instant;
import java.util.UUID;

public record HealthConsentResponseDTO(
        UUID id,
        UUID ownerId,
        UUID consentedBy,
        Instant consentedAt,
        String termVersion,
        String purpose,
        Instant revokedAt
) {
}
