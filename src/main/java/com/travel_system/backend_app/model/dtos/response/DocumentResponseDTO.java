package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponseDTO(
        UUID id,
        DocumentType documentType,
        DocumentStatus documentStatus,
        UUID healthConditionId,
        String originalFilename,
        String rejectionReason,
        long sizeBytes,
        LocalDate validUntil,
        Instant createdAt
) {
}
