package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.TravelStatus;

import java.time.Instant;
import java.util.UUID;

public record NewLocationReceivedEvents(
        UUID travelId,
        Double latitude,
        Double longitude,
        Instant timestamp,
        TravelStatus status,
        Double speed,
        Double heading) {
}
