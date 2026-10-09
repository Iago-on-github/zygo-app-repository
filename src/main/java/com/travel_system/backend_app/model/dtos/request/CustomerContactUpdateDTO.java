package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

public record CustomerContactUpdateDTO(
        @Email
        @Size(max = 30, message = "O email de contato deve ter até 30 caracteres")
        String contactEmail,

        @Size(min = 11, max = 11, message = "O telefone de contato deve ter até 11 caracteres")
        String contactTelephone
) {
}
