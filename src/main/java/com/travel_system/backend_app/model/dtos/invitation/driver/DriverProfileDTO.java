package com.travel_system.backend_app.model.dtos.invitation.driver;

import javax.validation.Valid;

public record DriverProfileDTO(
        @Valid
        DriverAcceptDTO driverAccept,

        @Valid
        DriverInvitationDTO driverInvitation
) {
}
