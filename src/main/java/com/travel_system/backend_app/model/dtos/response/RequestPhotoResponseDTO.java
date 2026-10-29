package com.travel_system.backend_app.model.dtos.response;

import java.time.Instant;

public record RequestPhotoResponseDTO(
        String url,
        Instant expiresAt,
        boolean IsPhotoNew
) {
}
