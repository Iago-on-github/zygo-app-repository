package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.HealthConditionSeverity;
import com.travel_system.backend_app.model.enums.HealthConditionType;

public record HealthConditionSummaryDTO(
        HealthConditionType type,
        HealthConditionSeverity severity,
        String description,
        String emergencyInstructions,
        String medication,
        String whereIsMedication
) {
}
