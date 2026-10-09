package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.InstitutionType;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public record StudentUpdateDTO(
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

        @Size(min = 11, max = 11, message = "o cpf deve ter 11 caracteres")
        String cpf,

        @Valid
        AddressUpdateDTO addressUpdate,

        @Valid
        InstitutionUpdateDTO institutionUpdate,

        @Size(max = 50, message = "O número da sua matrícula deve ter até 50 caracteres")
        String poolOfEnrollment,

        @Past(message = "A sua data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        String birthdate
) {
}
