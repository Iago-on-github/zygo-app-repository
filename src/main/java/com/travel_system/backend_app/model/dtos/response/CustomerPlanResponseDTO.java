package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.CustomerPlan;

public record CustomerPlanResponseDTO(
        CustomerPlan customerPlan,
        long maxAdministrators,
        long maxDrivers,
        long maxStudents,
        CustomerPlanUsageResponseDTO customerPlanUsageResponse

) {
}
