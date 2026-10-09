package com.travel_system.backend_app.model.dtos.response;

import java.time.Instant;
import java.util.UUID;

public record TravelScheduleResponseDTO(
        UUID id,
        String createdBy,
        Instant scheduledStartAt
) {
}
