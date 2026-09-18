package com.travel_system.backend_app.model.dtos.route;

import java.time.Instant;

public record LocationPointDTO(Double latitude, Double longitude, Instant timestamp) {
}
