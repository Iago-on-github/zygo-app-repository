package com.travel_system.backend_app.model.dtos.route;

import java.time.Instant;
import java.util.UUID;

public record GpsPayload(
        Double latitude,
        Double longitude,
        Double speed,
        Double heading,
        Instant timestamp,
        UUID travelId,
        UUID cityId,
        Double durationRemaining,
        Double distanceRemaining,
        String lastMovementState,
        Double lastCalcLat,
        Double lastCalcLng
) {
}
