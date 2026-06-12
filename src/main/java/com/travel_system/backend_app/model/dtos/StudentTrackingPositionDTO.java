package com.travel_system.backend_app.model.dtos;

import java.util.UUID;

public record StudentTrackingPositionDTO(
        UUID studentId,
        Double latitude,
        Double longitude
) {
}
