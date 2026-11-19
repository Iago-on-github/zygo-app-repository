package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.events.OwnerCacheInvalidationEvent;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.time.LocalDate;
import java.util.UUID;

public record ApprovedEvent(
        UUID id,
        String registrationNumber,
        UUID customerId,
        OwnerBuilt ownerBuilt,
        RegistrationRequestType type,
        LocalDate endTerm
) implements OwnerCacheInvalidationEvent {
    @Override
    public UUID ownerId() {
        return ownerBuilt.id();
    }

    @Override
    public OwnerType ownerType() {
        return ownerBuilt.type();
    }
}
