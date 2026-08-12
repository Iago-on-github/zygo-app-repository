package com.travel_system.backend_app.model.dtos.request;

import java.util.Set;
import java.util.UUID;

public record RouteStopStudentsRequestDTO(
        Set<UUID> studentIds
) {
}
