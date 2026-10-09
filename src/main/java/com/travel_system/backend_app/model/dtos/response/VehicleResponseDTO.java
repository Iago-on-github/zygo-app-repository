package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponseDTO(
        UUID id,
        String vehicleNumber,
        VehicleType vehicleType,
        String color,
        String numberPlate,
        String vehicleImage,
        int totalTrips,
        GeneralStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
