package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DriverResponseDTO(
        UUID id,
        String name,
        String lastName,
        String email,
        String telephone,
        String profilePicture,
        LocalDateTime createdAt,
        GeneralStatus status,
        String areaOfActivity,
        Integer totalTrips,
        CityResponseDTO city
) {
}
