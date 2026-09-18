package com.travel_system.backend_app.model.dtos.security;

import java.time.Instant;

public record RefreshTokenResponseDTO(String accessToken, String refreshToken, Instant expiresAt) {
}
