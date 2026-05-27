package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TravelResponseDTO(
        UUID id,
        TravelStatus status,
        DriverResponseDTO driverResponseDTO,
        Set<StudentTravel> studentTravel,
        Instant createdAt,
        Instant startHourTravel,
        TravelPreviewDTO travelPreviewDTO
        ) {
}
