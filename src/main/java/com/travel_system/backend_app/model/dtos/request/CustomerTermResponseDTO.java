package com.travel_system.backend_app.model.dtos.request;

import java.time.LocalDate;
import java.util.UUID;

public record CustomerTermResponseDTO(
        UUID id,
        String name,
        String startTerm,
        String endTerm
) {
}
