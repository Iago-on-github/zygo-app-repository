package com.travel_system.backend_app.model.dtos.response;

import java.time.LocalDate;
import java.util.UUID;

public record DriverCnhExpirationDTO(
        UUID driverId,
        String driverName,
        String driverLastName,
        UUID cnhId,
        LocalDate cnhExpirationDate
) {
}
