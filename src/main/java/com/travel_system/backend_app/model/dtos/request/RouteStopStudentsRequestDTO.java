package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record RouteStopStudentsRequestDTO(
        @NotNull
        UUID studentId,
        @NotNull
        TravelDirection travelDirection,
        @NotNull
        TravelPeriod travelPeriod
) {
}
