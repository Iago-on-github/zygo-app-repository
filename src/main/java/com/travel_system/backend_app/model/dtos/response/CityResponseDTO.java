package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.util.Set;
import java.util.UUID;

public record CityResponseDTO(UUID id, String name, CitySize size, GeneralStatus status, Set<UUID> customers) {
}
