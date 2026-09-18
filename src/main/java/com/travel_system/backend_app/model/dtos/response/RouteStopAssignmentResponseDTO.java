package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.TravelDirection;

import java.util.UUID;

public record RouteStopAssignmentResponseDTO(
        UUID routeStopId,
        String stopName,
        TravelDirection travelDirection,
        Integer stopSequence,
        boolean isOptionalStop
) {
}
