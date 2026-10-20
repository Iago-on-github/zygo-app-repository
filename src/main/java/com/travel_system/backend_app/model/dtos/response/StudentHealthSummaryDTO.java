package com.travel_system.backend_app.model.dtos.response;

import java.util.List;
import java.util.UUID;

public record StudentHealthSummaryDTO(
        UUID studentId,
        String studentName,
        boolean declarationOutdated,
        List<HealthConditionSummaryDTO> conditions,
        List<EmergencyContactDTO> emergencyContacts
) {
}
