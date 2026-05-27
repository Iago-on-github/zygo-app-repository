package com.travel_system.backend_app.model.dtos;

// Dto para exibição de preview do trajeto
public record TravelPreviewDTO(Double distance, Double duration, String destinationCity, String arrivalTime) {
}
