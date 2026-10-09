package com.travel_system.backend_app.model.dtos.request;

import org.springframework.cglib.core.Local;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDate;

public record AdministratorUpdateDTO(
        @Email(message = "Verifique a inserção do email")
        String email,

        @Size(min = 7, max = 75, message = "A senha deve ter entre 7 e 75 caracteres")
        String password,

        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @Pattern(regexp = "\\d{11}", message = "O telefone deve ter 11 dígitos")
        String telephone,

        @Size(min = 4, max = 20, message = "O seu cargo de responsável deve ter entre 4 e 20 caracteres")
        String jobTitle,

        @DateTimeFormat(pattern = "dd/MM/yyyy")
        @Past(message = "a data de nascimento deve estar no passado")
        LocalDate birthdate,

        @Valid
        AddressUpdateDTO addressUpdate
) {
}
