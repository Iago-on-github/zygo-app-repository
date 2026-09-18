package com.travel_system.backend_app.model.dtos.mapboxApi;

// usado para tracking instantâneo
public record CurrentVehicleLocationDTO(Double latitude, Double longitude, Double speed, Double heading) {
}
