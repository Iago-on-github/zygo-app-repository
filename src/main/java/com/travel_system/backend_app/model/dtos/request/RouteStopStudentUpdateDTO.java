package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelPeriod;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public record RouteStopStudentUpdateDTO(
        @NotNull
        UUID routeStopId,
        TravelPeriod travelPeriod
) {
}
