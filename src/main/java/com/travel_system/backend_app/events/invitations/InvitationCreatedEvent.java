package com.travel_system.backend_app.events.invitations;

import com.travel_system.backend_app.model.enums.TargetUserType;

import java.time.Instant;
import java.util.UUID;

public record InvitationCreatedEvent(
        UUID invitationId,
        UUID invitedUserAccountId,
        UUID customerId,
        TargetUserType targetUserType,
        Instant expiresAt,
        String pureToken
) {
    // evita que, caso o puretoken seja logado, o mesmo vaze
    @Override
    public String toString() {
        return "InvitationCreatedEvent[invitationId=" + invitationId + "]";
    }
}
