package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.HealthOwner;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentExpiredEvent(
        UUID documentId,
        UUID customerId,
        HealthOwner owner) {
}
