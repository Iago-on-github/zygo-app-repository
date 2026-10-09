package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerTermRequestDTO(
        @NotBlank
        @Size(min = 4, max = 50, message = "O nome precisa ter entre 4 e 50 caracteres")
        String name,

        @NotNull
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate startTerm,

        @NotNull
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate endTerm
) {
}
