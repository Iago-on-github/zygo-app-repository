package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.HealthCondition;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record HealthDeclarationUpdateDTO(
        @Valid List<HealthConditionRequestDTO> conditionsToAdd,
        @Valid List<HealthConditionUpdateDTO> conditionsToUpdate,
        List<UUID> conditionsIdsToRemove
) {
}
