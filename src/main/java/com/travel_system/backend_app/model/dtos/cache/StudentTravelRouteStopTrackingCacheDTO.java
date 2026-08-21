package com.travel_system.backend_app.model.dtos.cache;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.UUID;

/*
* dto estratégia 'getorload' para associação StudentTravel - RouteStop
* */

public record StudentTravelRouteStopTrackingCacheDTO(
        UUID studentTravelId,
        UUID studentId,
        UUID travelId,
        UUID routeStopId,
        Double routeStopLatitude,
        Double routeStopLongitude,
        TravelPeriod travelPeriod,
        StudentTravelRouteStopStatus status,
        Double monitoringThreshold
) {
}
