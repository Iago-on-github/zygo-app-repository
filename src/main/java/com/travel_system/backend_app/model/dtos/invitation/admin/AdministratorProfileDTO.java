package com.travel_system.backend_app.model.dtos.invitation.admin;

import javax.validation.Valid;

public record AdministratorProfileDTO(
        @Valid
        AdministratorInvitationDTO administratorInvitation,
        @Valid
        AdministratorAcceptDTO administratorAccept
) {
}
