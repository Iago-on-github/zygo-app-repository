package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.CustomerPlan;

public record CustomerPlanUsageResponseDTO(
        long totalAdmins,
        long totalDrivers,
        long totalStudents
) {
}
