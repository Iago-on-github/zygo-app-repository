package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.Email;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

public record DriverUpdateDTO(
        @Email
        String email,
        @Size(min = 7)
        String password,
        String name,
        String lastName,
        String telephone,
        String areaOfActivity
) {
}
