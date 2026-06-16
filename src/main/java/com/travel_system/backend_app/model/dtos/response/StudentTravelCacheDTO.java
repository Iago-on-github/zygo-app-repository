package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.StudentTravelStatus;

import java.util.UUID;

public record StudentTravelCacheDTO(UUID studentTravelId, String studentEmail, UUID studentId, StudentTravelStatus status, boolean embark) {
}
