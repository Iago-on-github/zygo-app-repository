package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentTravelRouteStopsCacheEvent(
        UUID studentTravelId,
        UUID travelId,
        UUID routeStopId,
        Double distance,
        Instant occurredAt,
        Double routeStopLatitude,
        Double routeStopLongitude,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus,
        Double distanceInMeters,
        Instant disembarkAt,
        Instant vehiclePositionAt,
        Double vehicleLatitude,
        Double vehicleLongitude
) {
}
