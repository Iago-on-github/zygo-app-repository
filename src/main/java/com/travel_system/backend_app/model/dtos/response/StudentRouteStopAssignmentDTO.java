package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.time.Instant;
import java.util.UUID;

public record StudentRouteStopAssignmentDTO(
        UUID id,
        UUID studentId,
        UUID routeStopId,
        TravelPeriod travelPeriod,
        Instant createdAt,
        Instant updatedAt
) {
}
