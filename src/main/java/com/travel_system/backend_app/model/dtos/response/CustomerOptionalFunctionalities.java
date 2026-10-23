package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.dtos.request.CustomerRegistrationNotificationSettingsDTO;
import com.travel_system.backend_app.model.enums.DocumentType;
import jakarta.validation.Valid;

import java.util.Set;

public record CustomerOptionalFunctionalities(
        boolean healthDeclarationRequestEnabled,

        @Valid
        CustomerRegistrationNotificationSettingsDTO registrationNotificationSettings
) {
}
