package com.travel_system.backend_app.model.dtos.invitation.responsible;

import javax.validation.Valid;

public record ResponsibleAdultProfileDTO(
        @Valid
        ResponsibleAdultInvitationDTO responsibleAdultInvitation,

        @Valid
        ResponsibleAdultAcceptDTO responsibleAdultAccept
) {
}
