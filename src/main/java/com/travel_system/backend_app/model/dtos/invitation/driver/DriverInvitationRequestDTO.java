package com.travel_system.backend_app.model.dtos.invitation.driver;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record DriverInvitationRequestDTO(
        @Email
        @NotBlank
        String email,

        @NotNull
        @Valid
        DriverInvitationDTO driverInvitation
) {
}
