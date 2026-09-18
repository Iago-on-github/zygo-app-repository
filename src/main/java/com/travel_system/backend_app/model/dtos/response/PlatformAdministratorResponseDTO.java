package com.travel_system.backend_app.model.dtos.response;

import java.util.UUID;

public record PlatformAdministratorResponseDTO(
        UUID id,
        String email,
        String name
) {
}
