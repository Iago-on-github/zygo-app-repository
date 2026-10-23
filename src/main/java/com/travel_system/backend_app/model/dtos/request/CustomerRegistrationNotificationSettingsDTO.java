package com.travel_system.backend_app.model.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CustomerRegistrationNotificationSettingsDTO(
        @Min(value = 1) @Max(value = 90)
        int registrationGracePeriodDays,
        @Min(value = 1) @Max(value = 90)
        int registrationRenewalWindowDays,
        @Min(value = 1) @Max(value = 90)
        int registrationBlockWarningDays
) {
}
