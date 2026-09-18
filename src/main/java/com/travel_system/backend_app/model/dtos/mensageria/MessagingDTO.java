package com.travel_system.backend_app.model.dtos.mensageria;

import java.time.Instant;
import java.util.UUID;

public record MessagingDTO(
        Double latitude,
        Double longitude,
        Double heading, //serve para mover o icon no mapa (0 -> 360)
        Double speed,
        Instant timestamp,
        UUID travelId) {
}
