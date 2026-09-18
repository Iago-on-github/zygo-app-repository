package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;

import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record DriverRequestDTO(
        @Email
        @NotBlank
        String email,
        @Size(min = 7)
        String password,
        @NotNull
        String name,
        String lastName,
        @NotNull
        String telephone,
        @NotNull
        @Past(message = "A data de nascimento deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate birthdate,
        @NotNull
        Set<Shift> driverShifts,
        String areaOfActivity
) {
}
