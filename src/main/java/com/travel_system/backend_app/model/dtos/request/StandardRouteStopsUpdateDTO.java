package com.travel_system.backend_app.model.dtos.request;

import java.util.Set;

public record StandardRouteStopsUpdateDTO(
        Set<RouteStopAssignmentRequestDTO> routeStops
) {
}
