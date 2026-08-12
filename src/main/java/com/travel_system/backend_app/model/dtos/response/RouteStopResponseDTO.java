package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RouteStopResponseDTO(
        UUID id,
        String name,
        String description,
        Set<UUID> studentIds,
        Double latitude,
        Double longitude,
        UUID customerId,
        Set<RouteStopAssignmentResponseDTO> routeStopAssignments,
        GeneralStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
