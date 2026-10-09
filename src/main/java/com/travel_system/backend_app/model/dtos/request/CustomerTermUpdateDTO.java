package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerTermUpdateDTO(
        @Size(min = 4, max = 50, message = "O nome precisa ter entre 4 e 50 caracteres")
        String name,

        @JsonFormat(pattern = "dd/MM/yyy")
        LocalDate startTerm,

        @JsonFormat(pattern = "dd/MM/yyy")
        LocalDate endTerm
) {
}
