package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record RouteStopRequestDTO(
        @NotNull
        String name,
        @NotNull
        String description,
        Set<UUID> studentIds,
        @NotNull
        Double latitude,
        @NotNull
        Double longitude

) {
}
