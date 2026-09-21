package com.travel_system.backend_app.model.dtos.request;



import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.UUID;

public record TravelRequestDTO(
        UUID standardRouteId,
        TravelPeriod travelPeriod,
        TravelDirection travelDirection,
        Double originLongitude,
        Double originLatitude,
        Double finalLongitude,
        Double finalLatitude,
        String destinationCity) {
}
