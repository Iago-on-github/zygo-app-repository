package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.Shift;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.Set;

public record CustomerCalendarRequestDTO(
        @NotNull
        @Future(message = "A data do feriado deve estar no futuro")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate holidayDate,
        @NotBlank
        String description,
        @NotEmpty
        Set<Shift> shifts
) {
}
