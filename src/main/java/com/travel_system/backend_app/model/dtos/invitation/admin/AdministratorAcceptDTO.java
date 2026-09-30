package com.travel_system.backend_app.model.dtos.invitation.admin;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.*;
import java.time.LocalDate;

public record AdministratorAcceptDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @NotBlank(message = "O sobrenome é obrigatório")
        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @NotBlank(message = "O CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos")
        String cpf,

        @NotNull
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate,

        @NotBlank(message = "Campo 'Telefone' é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "O telefone deve ter 11 dígitos")
        String telephone
) {
}
