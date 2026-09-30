package com.travel_system.backend_app.model.dtos.security;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record UserAccountRegisterDTO(
        @NotBlank(message = "O email é obrigatório")
        @Email
        String email,

        @NotBlank
        @Size(min = 7, max = 75, message = "A senha deve ter entre 7 e 75 caracteres")
        String password,

        @AssertTrue
        boolean acceptedTerms

) {
}
