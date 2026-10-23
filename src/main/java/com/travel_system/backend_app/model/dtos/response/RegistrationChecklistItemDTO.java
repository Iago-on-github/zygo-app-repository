package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.Document;
import com.travel_system.backend_app.model.enums.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record RegistrationChecklistItemDTO(
        DocumentType documentType,
        boolean sent,
        UUID documentId, // null caso nao enviado
        Instant uploadAt // null caso nao enviado
) {
}
