package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HealthDocumentApprovedEvent(
        UUID documentId,
        UUID customerId,
        OwnerBuilt owner,
        UUID reviewedBy,
        Instant reviewedAt,
        LocalDate validUntil
) {
}
