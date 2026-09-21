package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.UUID;

public record JoinTravelResponseDTO(
        UUID studentTravelId,
        UUID travelId,
        TravelPeriod travelPeriod,
        TravelDirection travelDirection,
        boolean isEmbark,
        StudentTravelStatus status,
        Long remainingAttemptsBeforeBlock,
        Integer nextBlockDurationMinutes
) {
}
