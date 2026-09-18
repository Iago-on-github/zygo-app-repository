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
        String password,
        @NotNull
        String name,
        String lastName,
        @NotNull @Min(value = 11, message = "O CPF deve ter no mínimo 11 digitos") @Max(value = 11, message = "O CPF deve ter no máximo 11 digitos")
        String cpf,
        @NotNull @Min(value = 11, message = "O número de telefone deve ter no mínimo 11 digitos") @Max(value = 11, message = "O número de telefone deve ter no máximo 11 digitos")
        String telephone,
        @NotNull
        ResponsibleAdultType responsibleAdultType,
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate
) {
}
