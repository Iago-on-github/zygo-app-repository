package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.util.UUID;

public record RegistrationClosedEvent(
        UUID registrationId,
        UUID customerId,
        OwnerBuilt ownerBuilt,
        String registrationNumber
) {
}
