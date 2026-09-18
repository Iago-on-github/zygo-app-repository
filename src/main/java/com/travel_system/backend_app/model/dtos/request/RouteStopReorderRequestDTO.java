package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelDirection;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.UUID;

public record RouteStopReorderRequestDTO(
        @NotNull
        UUID routeStopId,
        @NotNull
        TravelDirection travelDirection,
        @NotNull @Positive(message = "O valor deve ser maior que zero.")
        int newSequence) {
}
