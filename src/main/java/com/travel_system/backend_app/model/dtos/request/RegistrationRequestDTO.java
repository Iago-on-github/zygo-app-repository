package com.travel_system.backend_app.model.dtos.request;

import java.time.LocalDate;

public record RegistrationRequestDTO(
        String identityNumber,
        String identityIssuer,
        String motherName,
        String fatherName
) {
}
