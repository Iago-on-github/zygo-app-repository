package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.HealthPendingItemType;

import java.util.UUID;

public record HealthPendingItemDTO(
        HealthPendingItemType type,
        UUID healthConditionId,
        UUID documentId,
        String message
) {
}

/*
* uma pendência específica, com as referências para o app levar o usuário ao lugar certo
 * */
