package com.travel_system.backend_app.model.dtos.request;



import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.UUID;

public record TravelRequestDTO(
        UUID driverId,
        TravelPeriod travelPeriod,
        Double originLongitude,
        Double originLatitude,
        Double finalLongitude,
        Double finalLatitude,
        String destinationCity) {
}
