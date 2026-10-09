package com.travel_system.backend_app.model.dtos.invitation.admin;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record AdministratorInvitationRequestDTO(
        @Email
        @NotBlank
        String email,

        @NotNull
        @Valid
        AdministratorInvitationDTO admInvitation
) {
}
