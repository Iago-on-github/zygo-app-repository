package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.events.OwnerCacheInvalidationEvent;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

public record ClosedEvent(
        UUID registrationId,
        UUID customerId,
        OwnerBuilt ownerBuilt,
        String registrationNumber
) implements OwnerCacheInvalidationEvent {
    @Override
    public OwnerType ownerType() {
        return ownerBuilt.type();
    }

    @Override
    public UUID ownerId() {
        return ownerBuilt.id();
    }
}
