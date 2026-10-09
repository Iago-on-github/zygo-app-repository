package com.travel_system.backend_app.events.send_emails;

import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record SensitiveOperationCreatedEvent(
        String pureToken,
        SensitiveOperationType sensitiveOperationType,
        Instant expiresAt
) {
    @Override
    public @NonNull String toString() {
        return "SensitiveOperationCreatedEvent{" +
                "pureToken=' PURE TOKEN P/ EVITAR VAZAMENTOS"  + '\'' +
                '}';
    }
}
