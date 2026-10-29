package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransportRegistrationReviewDTO(
        UUID id,
        RegistrationRequestType type,
        String ownerName,
        Instant submittedAt,
        String registrationNumber,
        OwnerType ownerType
) {
}
