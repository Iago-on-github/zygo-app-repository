package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountReactivatedEvent(UUID userAccountId, Instant reactiveAt) {
}
