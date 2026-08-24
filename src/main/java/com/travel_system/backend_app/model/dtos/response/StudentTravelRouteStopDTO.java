package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentTravelRouteStopDTO(
        UUID id,
        UUID studentTravelId,
        RouteStopResponseDTO RouteStopResponseDTO,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus,
        Instant lastValidatedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
