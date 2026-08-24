package com.travel_system.backend_app.model.dtos.mapboxApi;

import java.time.Instant;

// usado como DTO de agregação para respostas real-time
public record LiveLocationDTO(
        Double latitude,
        Double longitude,
        String geometry,
        Double distance,
        Double lastCalcLat,
        Double lastCalcLng,
        Instant current_location_timestamp) {
}
