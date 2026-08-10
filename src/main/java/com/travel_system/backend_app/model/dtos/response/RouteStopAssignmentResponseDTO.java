package com.travel_system.backend_app.model.dtos.response;

import java.util.UUID;

public record RouteStopAssignmentResponseDTO(
        UUID routeStopId,
        String stopName,
        Integer stopSequence,
        boolean isOptionalStop
) {
}
