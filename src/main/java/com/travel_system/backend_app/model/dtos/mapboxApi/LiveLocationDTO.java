package com.travel_system.backend_app.model.dtos.mapboxApi;

public record LiveLocationDTO(
        Double latitude,
        Double longitude,
        String geometry,
        Double distance,
        Double lastCalcLat,
        Double lastCalcLng) {
}
