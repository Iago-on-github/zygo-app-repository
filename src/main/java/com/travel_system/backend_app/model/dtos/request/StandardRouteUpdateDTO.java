package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;

public record StandardRouteUpdateDTO(
        String routeName,
        String routeDescription,
        TravelPeriod period,
        Double originLatitude,
        Double originLongitude,
        Double destinationLatitude,
        Double destinationLongitude
) {
}
