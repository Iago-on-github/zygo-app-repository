package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.ResponsibleAdultType;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ResponsibleAdultResponseDTO(
        UUID id,
        String email,
        String name,
        String lastName,
        String cpf,
        String telephone,
        Set<StudentResponsibleAdultDTO> studentResponsibleAdultDTO,
        ResponsibleAdultType responsibleAdultType,
        String profilePicture,
        LocalDate birthdate,
        GeneralStatus status,
        UUID customerId,
        Instant createdAt,
        Instant updatedAt
) {
}

// id, name, cpf, responsibleAdultType
