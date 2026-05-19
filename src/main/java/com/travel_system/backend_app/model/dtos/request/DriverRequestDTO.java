package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

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
        String areaOfActivity
) {
}
