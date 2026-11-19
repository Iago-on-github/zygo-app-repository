package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.events.OwnerCacheInvalidationEvent;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

public record RejectedEvent(
        UUID id,
        UUID transportRegistrationId,
        UUID customerId,
        OwnerBuilt ownerBuilt,
        String rejectionReason
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
