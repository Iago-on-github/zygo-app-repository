package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;

import java.time.Instant;
import java.util.UUID;

/*
* dto de evento para confirmação da chegada e desembarque do estudante
* */

public record ConfirmStudentTravelRouteStopReachedEvent(
        UUID studentTravelId,
        UUID studentId,
        UUID travelId,
        UUID routeStopId,

        Double vehicleLatitude,
        Double vehicleLongitude,

        Double distanceInMeters,

        Instant disembarkAt,
        Instant vehiclePositionAt,

        StudentTravelStatus studentTravelStatus
) {
}
