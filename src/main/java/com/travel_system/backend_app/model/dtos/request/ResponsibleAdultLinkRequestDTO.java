package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.StudentRelationshipType;

import javax.validation.constraints.NotNull;
import java.util.UUID;

public record ResponsibleAdultLinkRequestDTO(
        UUID responsibleAdultId,
        StudentRelationshipType studentRelationshipType
) {
}
