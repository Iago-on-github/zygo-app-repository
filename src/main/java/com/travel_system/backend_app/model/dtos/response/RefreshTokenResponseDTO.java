package com.travel_system.backend_app.model.dtos.response;

import java.time.Instant;

public record RefreshTokenResponseDTO(String accessToken, String refreshToken, Instant expiresAt) {
}
