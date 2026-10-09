package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record StudentResponseDTO(
        UUID id,
        String name,
        String lastName,
        String email,
        String telephone,
        String cpf,
        GeneralStatus status,
        String profilePicture,
        AddressResponseDTO addressResponse,
        List<StudentEnrollmentResponseDTO> studentEnrollmentResponse,
        UUID responsibleAdultId,
        Set<Shift> shifts,
        UUID customerId,
        Instant createdAt,
        Instant updatedAt
        ) {
}
