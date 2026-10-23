package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.HealthOwner;

import java.time.Instant;
import java.util.UUID;

public record HealthDocumentRejectedEvent(
        UUID documentId,
        UUID customerId,
        HealthOwner owner,
        UUID reviewedBy,
        Instant reviewedAt
) {
}
