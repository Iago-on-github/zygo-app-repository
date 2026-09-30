package com.travel_system.backend_app.events.send_emails;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record EmailVerificationRequestedEvent(
        UUID userAccountId,
        String email,
        String pureToken,
        Instant expiresAt
) {

    @Override
    public @NonNull String toString() {
        return "EmailVerificationRequestedEvent[userAccountId=" + userAccountId + "]";
    }
}
