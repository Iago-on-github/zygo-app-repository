package com.travel_system.backend_app.model.dtos.invitation.student;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.dtos.request.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDate;

public record StudentAcceptDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @NotBlank(message = "O sobrenome é obrigatório")
        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @Size(min = 11, max = 11, message = "o cpf deve ter 11 caracteres")
        String cpf,

        @NotBlank(message = "Campo de telefone é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "O telefone deve ter 11 dígitos")
        String telephone,

        @NotNull
        @Past
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate,

        @Valid
        AddressRequestDTO addressRequest,

        @Valid
        StudentInstitutionRequestDTO studentInstitutionRequest
) {
}

// usado no body do método accept
