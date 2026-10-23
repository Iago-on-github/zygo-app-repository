package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.HealthOwner;

import java.util.UUID;

public record HealthDocumentExpiredEvent(
        UUID documentId,
        UUID customerId,
        HealthOwner owner) {
}
