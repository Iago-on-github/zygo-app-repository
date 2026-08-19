package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.time.Instant;
import java.util.UUID;

public record InvalidStudentTravelRouteStopEvent(
        UUID studentTravelId,
        UUID studentId,
        UUID travelId,
        UUID customerId,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus,
        Instant lastValidatedAt
) {
}
