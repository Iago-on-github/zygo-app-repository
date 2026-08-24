package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.dtos.request.RouteStopAssignmentRequestDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record StandardRouteResponseDTO(
        UUID id,
        String routeName,
        String routeDescription,
        Double originLatitude,
        Double originLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        String standardGeometry,
        Set<TravelPeriod> travelPeriods,
        Set<RouteStopAssignmentResponseDTO> routeStopAssignments,
        UUID customerId,
        GeneralStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
