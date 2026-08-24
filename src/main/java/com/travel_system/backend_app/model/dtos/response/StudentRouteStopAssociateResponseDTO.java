package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record StudentRouteStopAssociateResponseDTO(
        UUID id,
        String name,
        String description,
        Set<UUID> studentIds,
        Double latitude,
        Double longitude,
        UUID customerId,
        Set<RouteStopAssignmentResponseDTO> routeStopAssignments,
        StandardRouteSimpleResponseDTO standardRoute,
//        RouteStopSimpleResponseDTO routeStop,
        Set<TravelPeriod> travelPeriods,
        GeneralStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}