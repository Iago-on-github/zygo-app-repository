package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.GeoPosition;

import java.time.Instant;
import java.util.UUID;

public record StudentTravelResponseDTO(UUID id,
                                       UUID travelId,
                                       UUID studentId,
                                       Instant embarkHour,
                                       Instant disembarkHour,
                                       GeoPosition position) {
}
