package com.travel_system.backend_app.model.dtos.request;


import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record AdministratorRequestDTO(
        @Email(message = "Verifique a inserção do email")
        @NotNull
        @NotBlank(message = "Campo 'Email' é obrigatório")
        String email,
        @NotNull
        @Min(7)
        @NotBlank(message = "Campo 'Senha' é obrigatório")
        String password,
        @NotNull
        @Min(4)
        @NotBlank(message = "Campo 'Nome' é obrigatório")
        String name,
        String lastName,
        @NotNull
        @NotBlank
        @Min(11)
        String cpf,
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate,
        String jobTitle,
        @NotNull
        @Min(8)
        @NotBlank(message = "Campo 'Telefone' é obrigatório")
        String telephone
) {
}
