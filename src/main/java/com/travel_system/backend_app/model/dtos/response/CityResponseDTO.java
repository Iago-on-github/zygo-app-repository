package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.CitySize;

import java.util.UUID;

public record CityResponseDTO(UUID id, String name, CitySize size) {
}
