package com.travel_system.backend_app.events.routestops_algorithm;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.time.Instant;
import java.util.UUID;

public record CancelledStudentTravelRouteStopEvent(
        UUID studentTravelId,
        UUID studentId,
        UUID travelId,
        UUID routeStopId,
        UUID customerId,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus,
        Instant lastValidatedAt
) {
}
