package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.events.OwnerCacheInvalidationEvent;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.util.UUID;

// evento para notificar os admins do Customer
public record SubmittedEvent(
        UUID registrationRequestId,
        UUID registrationId,
        UUID customerId,
        OwnerBuilt owner,
        RegistrationRequestType requestType
) implements OwnerCacheInvalidationEvent {
    @Override
    public OwnerType ownerType() {
        return owner.type();
    }

    @Override
    public UUID ownerId() {
        return owner.id();
    }
}
