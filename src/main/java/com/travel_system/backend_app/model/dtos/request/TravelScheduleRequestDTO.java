package com.travel_system.backend_app.model.dtos.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TravelScheduleRequestDTO(
        @NotNull
        @Future
        Instant scheduledStartAt
) {
}
