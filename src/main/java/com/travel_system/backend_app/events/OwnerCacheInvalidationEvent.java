package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

/*
 * implementada em eventos onde altera a situação da matricula de um titular, formalizando que o cache deve ser limpo
 * */

public interface OwnerCacheInvalidationEvent {
    UUID customerId();
    OwnerType ownerType();
    UUID ownerId();

}
