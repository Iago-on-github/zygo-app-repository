package com.travel_system.backend_app.model.dtos.invitation;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import java.time.LocalDate;

public record StudentAcceptDTO(
        @NotNull
        String name,
        @NotNull
        String lastName,
        @NotNull
        String telephone,
        @NotNull
        @Past @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate
) {
}

// usado no body do método accept
