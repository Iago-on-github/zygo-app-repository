package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;

import java.time.Instant;
import java.util.UUID;

public record ActiveStudentTravelDTO(
        UUID studentTravelId,
        UUID travelId,
        UUID routeId,
        String routeName,
        String routeDescription,
        UUID driverId,
        String driverName,
        TravelPeriod period,
        TravelDirection travelDirection,
        UUID cityId,
        String cityName) {
}
