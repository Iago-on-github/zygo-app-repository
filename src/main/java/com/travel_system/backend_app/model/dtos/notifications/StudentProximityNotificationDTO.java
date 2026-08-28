package com.travel_system.backend_app.model.dtos.notifications;

import java.util.UUID;

public record StudentProximityNotificationDTO(
        UUID travelId,
        UUID studentId,
        UUID customerId,
        Double distance,
        String zone,
        String timestamp,
        String alertType) {
}
