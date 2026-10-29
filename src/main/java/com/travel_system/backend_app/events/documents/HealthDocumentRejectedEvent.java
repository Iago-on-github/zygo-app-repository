package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.time.Instant;
import java.util.UUID;

public record HealthDocumentRejectedEvent(
        UUID documentId,
        UUID customerId,
        OwnerBuilt owner,
        UUID reviewedBy,
        Instant reviewedAt
) {
}
