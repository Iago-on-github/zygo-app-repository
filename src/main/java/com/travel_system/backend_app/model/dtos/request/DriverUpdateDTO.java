package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDate;

public record DriverUpdateDTO(
        @Email(message = "Verifique a inserção do email")
        String email,

        @Size(min = 7, max = 75, message = "A senha deve ter entre 7 e 75 caracteres")
        String password,

        @Size(min = 4, max = 10, message = "O nome deve ter entre 4 e 10 caracteres")
        String name,

        @Size(max = 10, message = "O sobrenome deve ter no máximo 10 caracteres")
        String lastName,

        @Size(min = 11, max = 11, message = "o cpf deve ter 11 caracteres")
        String cpf,

        @Pattern(regexp = "\\d{11}", message = "O telefone deve ter 11 dígitos")
        String telephone,

        String areaOfActivity,

        @JsonFormat(pattern = "dd/MM/yyyy")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate birthdate,

        @Valid
        AddressUpdateDTO addressUpdate,

        @Valid
        CnhUpdateDTO cnhUpdate
) {
}
