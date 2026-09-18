package com.travel_system.backend_app.model.dtos.route;

import com.travel_system.backend_app.model.enums.TravelDirection;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.UUID;

public record AssociateRouteStopToStandardRouteDTO(
        @NotNull
        @Positive(message = "O valor deve ser maior que zero.")
        int sequence,
        @NotNull
        boolean isOptionalSpot,
        @NotNull
        TravelDirection travelDirection
) {
}
