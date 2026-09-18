package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record RouteStopRequestDTO(
        @NotNull
        String name,
        @NotNull
        String description,
        @NotNull
        Double latitude,
        @NotNull
        Double longitude

) {
}
