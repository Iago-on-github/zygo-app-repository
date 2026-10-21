package com.travel_system.backend_app.model.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ScheduledTravelRequestDTO(
        @Valid @NotNull TravelScheduleRequestDTO schedule,
        @Valid @NotNull TravelRequestDTO travel
) {
}
