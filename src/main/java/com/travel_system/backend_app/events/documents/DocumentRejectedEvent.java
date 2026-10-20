package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.HealthOwner;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentRejectedEvent(
        UUID documentId,
        UUID customerId,
        UUID userAccountId,
        UUID reviewedBy,
        Instant reviewedAt
) {
}
