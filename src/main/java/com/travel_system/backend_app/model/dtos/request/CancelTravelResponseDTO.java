package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.Vehicle;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.time.Instant;
import java.util.UUID;

public record CancelTravelResponseDTO(
        UUID travelId,
        String vehicleNumber,
        TravelPeriod travelPeriod,
        TravelDirection travelDirection,
        String cancelledReason,
        Instant cancelledAt
) {
}
