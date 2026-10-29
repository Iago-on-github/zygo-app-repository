package com.travel_system.backend_app.events.documents;

import java.util.UUID;

public record NextTermMissingEvent(UUID customerId, int affectedRegistrations) {
}
