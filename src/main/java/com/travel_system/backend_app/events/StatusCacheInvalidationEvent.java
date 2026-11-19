package com.travel_system.backend_app.events;

/*
* limpa o cache remanescente de titulares onde há mudança efetiva no banco conforme operações da matrícula acontecem
* ex.: mudança de status
* */

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

public record StatusCacheInvalidationEvent(
        UUID customerId,
        OwnerBuilt ownerBuilt
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
