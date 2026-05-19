package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @Email(message = "insira um email válido")
        @NotBlank(message = "email obrigatório")
        String email,
        @NotBlank(message = "senha obrigatória")
        String password) {
}
