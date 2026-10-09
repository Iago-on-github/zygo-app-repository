package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.CnhCategory;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CnhResponseDTO(
        UUID id,
        String cnhNumber,
        Set<CnhCategory> cnhCategories,
        LocalDate cnhExpirationDate,
        LocalDate cnhFirstIssueDate,
        Instant createdAt,
        Instant updatedAt
) {
}
