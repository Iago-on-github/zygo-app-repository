package com.travel_system.backend_app.model.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.model.HealthCondition;
import com.travel_system.backend_app.model.dtos.request.CustomerTermResponseDTO;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HealthDeclarationResponseDTO(
        UUID id,
        UUID ownerId,
        boolean hasCondition,
        List<HealthCondition> conditions,
        HealthDeclarationStatus healthDeclarationStatus,
        CustomerTermResponseDTO customerTermResponse,
        UUID declaredBy,
        Instant declaredAt
) {
}
