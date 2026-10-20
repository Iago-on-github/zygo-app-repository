package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.HealthCondition;
import jakarta.validation.Valid;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public record HealthDeclarationRequestDTO(
        boolean hasCondition,
        @Valid List<HealthConditionRequestDTO> conditions
) {
}
