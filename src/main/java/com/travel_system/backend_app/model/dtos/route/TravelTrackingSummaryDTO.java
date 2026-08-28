package com.travel_system.backend_app.model.dtos.route;

import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.TravelStatus;

import java.time.Instant;

public record TravelTrackingSummaryDTO(
        Double latitude,
        Double longitude,
        String geometry,
        Double distanceRemaining,
        Double durationRemaining,
        String movementState,
        String travelStatus,
        Double lastCalcLat,
        Double lastCalcLng,
        Instant current_location_timestamp
) {
}
