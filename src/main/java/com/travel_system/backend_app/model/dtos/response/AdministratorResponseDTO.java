package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdministratorResponseDTO(
        UUID id,
        String email,
        String name,
        String lastName,
        String birthDate,
        String telephone,
        String profilePicture,
        GeneralStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID customerId
) {
}
