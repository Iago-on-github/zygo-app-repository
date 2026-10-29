package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.util.UUID;

public record HealthDocumentExpiredEvent(
        UUID documentId,
        UUID customerId,
        OwnerBuilt owner) {
}
