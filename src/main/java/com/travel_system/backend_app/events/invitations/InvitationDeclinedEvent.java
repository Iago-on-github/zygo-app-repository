package com.travel_system.backend_app.events.invitations;

import java.util.UUID;

public record InvitationDeclinedEvent(
        UUID invitationId,
        UUID customerId,
        UUID invitedById,
        UUID invitedUserAccountId
) {
}
