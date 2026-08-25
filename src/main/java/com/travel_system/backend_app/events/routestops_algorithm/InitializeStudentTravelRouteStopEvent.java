package com.travel_system.backend_app.events.routestops_algorithm;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.util.UUID;

public record InitializeStudentTravelRouteStopEvent(
        UUID studentTravelId,
        UUID travelId,
        UUID routeStopId,
        Double routeStopLatitude,
        Double routeStopLongitude,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus
) {
}
