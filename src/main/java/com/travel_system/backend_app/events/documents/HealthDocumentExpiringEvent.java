package com.travel_system.backend_app.events.documents;

import java.time.LocalDate;
import java.util.UUID;

public record HealthDocumentExpiringEvent(
        UUID documentId,
        UUID customerId,
        UUID userAccountId,
        LocalDate validUntil,
        long daysLeft
) {
}
