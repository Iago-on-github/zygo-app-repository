package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.HealthOwner;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentExpiringEvent(
        UUID documentId,
        UUID customerId,
        UUID userAccountId,
        LocalDate validUntil,
        long daysLeft
) {
}
