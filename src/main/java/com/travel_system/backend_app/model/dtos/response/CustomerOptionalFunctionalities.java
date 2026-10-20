package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.DocumentType;

import java.util.Set;

public record CustomerOptionalFunctionalities(
        boolean healthDeclarationRequestEnabled,
        boolean studentEnrollmentRequestEnabled,
        Set<DocumentType> enrollmentRequiredTypes
) {
}
