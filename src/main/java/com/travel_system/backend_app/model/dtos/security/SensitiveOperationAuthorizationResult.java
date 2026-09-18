package com.travel_system.backend_app.model.dtos.security;

import com.travel_system.backend_app.model.SensitiveOperation;

public record SensitiveOperationAuthorizationResult(
        String pureToken,
        SensitiveOperation sensitiveOperation
) {
}
