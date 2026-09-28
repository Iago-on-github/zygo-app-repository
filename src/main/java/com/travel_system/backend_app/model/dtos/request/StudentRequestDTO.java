package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record StudentRequestDTO(
        @Email
        @NotBlank
        String email,
        @Size(min = 7)
        String password) {
}

// os dados de pessoais e de customer serão inseridos assim que o estudante adentrar em um customer