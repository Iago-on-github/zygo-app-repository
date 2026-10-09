package com.travel_system.backend_app.model.dtos.invitation.student;

import com.travel_system.backend_app.model.enums.InstitutionType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record InstitutionCatalogResponseDTO(
        UUID institutionId,
        String institutionName,
        InstitutionType institutionType,
        List<CatalogCourseDTO> courses
) {
    public record CatalogCourseDTO(
            UUID courseId,
            String courseName
    ) {}
}
