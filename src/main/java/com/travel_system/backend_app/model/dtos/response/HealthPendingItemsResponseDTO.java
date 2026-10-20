package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.model.enums.HealthConditionType;
import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record HealthPendingItemsResponseDTO(
        OwnerType ownerType,
        UUID ownerId,
        boolean hasPendingItems,
        List<HealthPendingItemDTO> items
) {
}
