package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.dtos.request.InstitutionCourseResponseDTO;
import com.travel_system.backend_app.model.enums.InstitutionType;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record InstitutionResponseDTO(
        UUID id,
        String institutionName,
        InstitutionType institutionType,
        Set<UUID> studentIds,
        List<InstitutionCourseResponseDTO> courses,
        Instant createdAt,
        Instant updatedAt
) {
}
