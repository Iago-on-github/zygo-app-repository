package com.travel_system.backend_app.events.send_emails;

import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import java.time.Instant;

public record SensitiveOperationCreatedEvent(
        String pureToken,
        SensitiveOperationType sensitiveOperationType,
        Instant expiresAt
) {
}
