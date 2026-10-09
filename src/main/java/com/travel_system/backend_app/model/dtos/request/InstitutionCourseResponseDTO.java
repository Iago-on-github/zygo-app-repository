package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.util.UUID;

public record InstitutionCourseResponseDTO(
        UUID id,
        String courseName,
        GeneralStatus status
) {
}
