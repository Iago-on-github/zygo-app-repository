package com.travel_system.backend_app.model.dtos.cache;

import com.travel_system.backend_app.model.dtos.response.StudentTravelRouteStopDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;

import java.util.List;
import java.util.UUID;

public record StudentTravelCacheDTO(
        UUID studentTravelId,
        String studentEmail,
        UUID studentId,
        StudentTravelStatus status,
        boolean embark
)
{
}
