package com.travel_system.backend_app.model.dtos;

import java.util.UUID;

public record ProfileRef(UUID ownerId, UUID customerId) {
}
