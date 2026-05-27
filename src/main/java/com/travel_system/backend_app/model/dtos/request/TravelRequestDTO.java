package com.travel_system.backend_app.model.dtos.request;



import java.util.UUID;

public record TravelRequestDTO(
        UUID driverId,
        Double originLongitude,
        Double originLatitude,
        Double finalLongitude,
        Double finalLatitude,
        String destinationCity) {
}
