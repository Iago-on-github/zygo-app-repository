package com.travel_system.backend_app.model.dtos.invitation.student;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.*;
import java.time.LocalDate;

public record StudentAcceptDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @NotBlank(message = "O sobrenome é obrigatório")
        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @NotBlank(message = "Campo 'Telefone' é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "O telefone deve ter 11 dígitos")
        String telephone,

        @NotNull
        @Past
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate
) {
}

// usado no body do método accept
