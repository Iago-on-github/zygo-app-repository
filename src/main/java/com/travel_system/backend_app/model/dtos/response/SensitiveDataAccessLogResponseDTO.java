package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.Action;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record SensitiveDataAccessLogResponseDTO(
        UUID id,
        UUID actorAccountId,
        OwnerType ownerType,
        UUID ownerId,
        ResourceType resourceType,
        UUID resourceId,
        Action action,
        UUID contextId,
        Instant accessAt
) {
}
