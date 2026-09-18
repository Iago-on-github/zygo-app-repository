package com.travel_system.backend_app.model.dtos.response;

import java.time.Instant;

public record LastLocationDTO(Double latitude,
                              Double longitude,
                              Long timestamp) {
}
