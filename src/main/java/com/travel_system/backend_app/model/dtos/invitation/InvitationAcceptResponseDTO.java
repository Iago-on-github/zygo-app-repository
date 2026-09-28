package com.travel_system.backend_app.model.dtos.invitation;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;

import java.util.UUID;

public record InvitationAcceptResponseDTO(
        String accessToken,
        String refreshToken,
        UserAccountType userAccountType,
        UUID profileId,
        UUID customerId,
        UUID invitedById,
        String customerName,
        GeneralStatus profileStatus
) {
}
