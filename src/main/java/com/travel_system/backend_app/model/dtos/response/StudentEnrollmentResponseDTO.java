package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.util.UUID;

public record StudentEnrollmentResponseDTO(
        UUID enrollmentId,
        UUID institutionId,
        String institutionName,
        UUID courseId,
        String courseName,
        String poolOfEnrollment,
        GeneralStatus status
) {
}
