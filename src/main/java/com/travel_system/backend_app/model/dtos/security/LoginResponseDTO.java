package com.travel_system.backend_app.model.dtos.security;

import java.time.Instant;

public record LoginResponseDTO(
        String username,
        Boolean authenticated,
        Instant created,
        Instant expiration,
        String accessToken,
        String refreshToken) {
}
