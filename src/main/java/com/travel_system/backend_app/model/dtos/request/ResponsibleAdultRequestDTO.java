package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.ResponsibleAdultType;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;

import javax.validation.constraints.*;
import java.time.LocalDate;

public record ResponsibleAdultRequestDTO(
        @NotNull @Email
        String email,
        @NotNull
        @Min(value = 7, message = "a senha deve conter ao menos 7 caracteres")
        String password
) {
}
