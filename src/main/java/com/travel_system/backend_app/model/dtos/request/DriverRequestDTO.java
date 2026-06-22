package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import javax.validation.constraints.*;
import java.util.UUID;

public record DriverRequestDTO(
        @Email
        @NotBlank
        String email,
        @Size(min = 7)
        String password,
        String name,
        String lastName,
        String telephone,
        String profilePicture,
        String areaOfActivity,
        @NotNull @NotBlank
        UUID customerId
) {
}
