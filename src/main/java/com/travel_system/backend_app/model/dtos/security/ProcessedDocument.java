package com.travel_system.backend_app.model.dtos.security;

public record ProcessedDocument(
        byte[] content,
        String contentType,
        long sizeBytes,
        String sha256
) {
}
