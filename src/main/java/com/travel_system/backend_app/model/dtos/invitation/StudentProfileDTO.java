package com.travel_system.backend_app.model.dtos.invitation;

import javax.validation.Valid;

public record StudentProfileDTO(
        @Valid
        StudentInvitationDTO studentInvitation,
        @Valid
        StudentAcceptDTO studentAccept
) {
}
