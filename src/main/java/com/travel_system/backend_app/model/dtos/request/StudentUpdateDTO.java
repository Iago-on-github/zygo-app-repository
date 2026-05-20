package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.InstitutionType;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

public record StudentUpdateDTO(
        @Email
        String email,
        @Size(min = 7)
        String password,
        @Size(min = 4)
        String name,
        String lastName,
        @Size(min = 9)
        String telephone,
        String profilePicture,
        InstitutionType institutionType,
        String course
) {
}
