package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.HealthConditionSeverity;
import com.travel_system.backend_app.model.enums.HealthConditionType;
import jakarta.validation.constraints.NotNull;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record HealthConditionRequestDTO(
        @NotNull HealthConditionType type,
        @NotNull HealthConditionSeverity severity,
        @NotBlank @Size(max = 300) String description,
        @NotBlank @Size(max = 500) String emergencyInstructions,
        @Size(max = 255) String medication,
        @Size(max = 255) String whereIsMedication,
        @Size(max = 255) String emergencyContactName,
        @Size(max = 30) String emergencyContactTelephone
) {
}
