package com.travel_system.backend_app.model.dtos.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.travel_system.backend_app.model.enums.TargetUserType;

import java.time.Instant;
import java.util.UUID;

public record MyInvitationResponseDTO(
        UUID id,
        UUID customerId,
        String customerName,
        TargetUserType targetUserType,
        String invitedByName,
        JsonNode profileData,
        Instant createdAt,
        Instant expiresAt
) {
}
