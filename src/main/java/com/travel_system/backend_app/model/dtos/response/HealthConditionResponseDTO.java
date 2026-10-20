package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.HealthConditionSeverity;
import com.travel_system.backend_app.model.enums.HealthConditionType;
import jakarta.validation.constraints.NotNull;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

public record HealthConditionResponseDTO(
        UUID id,
        HealthConditionType type,
        HealthConditionSeverity severity,
        String description,
        String emergencyInstructions,
        String medication,
        String whereIsMedication,
        String emergencyContactName,
        String emergencyContactTelephone
) {
}
