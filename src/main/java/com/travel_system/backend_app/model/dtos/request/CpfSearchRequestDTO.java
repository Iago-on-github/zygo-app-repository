package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public record CpfSearchRequestDTO(
        @NotBlank
        @Pattern(regexp = "\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", message = "CPF inválido")
        String cpf
) {
}
