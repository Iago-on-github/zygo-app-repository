package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.model.enums.TargetUserType;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponseDTO(
        UUID id,
        UUID invitedUserAccountId,
        String invitedIdentifier,
        TargetUserType targetUserType,
        InvitationStatus invitationStatus,
        UUID invitedBy,
        String invitedByName,
        Instant expiresAt,
        Instant createdAt,
        Instant respondedAt
) {}
