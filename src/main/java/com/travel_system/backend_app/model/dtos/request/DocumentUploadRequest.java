package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.DocumentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentUploadRequest(
        @NotNull DocumentType documentType,
        @NotNull UUID healthConditionId,
        @Future LocalDate validUntil
) {
}
