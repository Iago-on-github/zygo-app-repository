package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.ResponsibleAdultType;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record StudentResponsibleAdultDTO(
        @NotNull
        UUID studentId,
        @NotNull
        StudentRelationshipType studentRelationshipType
) {
}
