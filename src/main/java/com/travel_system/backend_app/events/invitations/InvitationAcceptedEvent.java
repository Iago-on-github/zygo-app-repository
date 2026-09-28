package com.travel_system.backend_app.events.invitations;

import java.time.Instant;
import java.util.UUID;

public record InvitationAcceptedEvent(
        UUID invitationId,
        UUID customerId,
        UUID invitedBy,
        UUID userAccountId,
        Instant acceptedInvitationAt
) {
}
