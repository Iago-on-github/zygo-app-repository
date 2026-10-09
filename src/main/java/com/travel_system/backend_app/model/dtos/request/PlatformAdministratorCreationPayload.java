package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.interfaces.SensitiveOperationData;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import javax.validation.constraints.*;

public record PlatformAdministratorCreationPayload(
        String email,

        String hashPassword
) implements SensitiveOperationData {
    @Override
    public SensitiveOperationType sensitiveType() {
        return SensitiveOperationType.CREATE_PLATFORM_ADMIN;
    }
}
