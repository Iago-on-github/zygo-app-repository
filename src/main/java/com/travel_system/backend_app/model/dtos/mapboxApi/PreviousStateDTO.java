package com.travel_system.backend_app.model.dtos.mapboxApi;

import java.time.Instant;

public record PreviousStateDTO(
        Double durationRemaining, // o ETA
        Double distanceRemaining,
        Long timeStamp
) {
}
