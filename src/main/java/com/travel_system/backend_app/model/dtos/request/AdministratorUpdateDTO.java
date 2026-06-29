package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.Email;

public record AdministratorUpdateDTO(
        @Email(message = "Verifique a inserção do email.")
        String email,
        String password,
        String name,
        String lastName,
        String telephone
) {
}
