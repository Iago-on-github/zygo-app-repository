package com.travel_system.backend_app.model.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CustomerRegistrationNotificationSettingsDTO(
        @Min(value = 1, message = "O período mínimo de carência é 1") @Max(value = 90, message = "O período máximo de carência é 90")
        int registrationGracePeriodDays,
        @Min(value = 1, message = "O período mínimo de renovação é 1") @Max(value = 90, message = "O período máximo de renovação é 90")
        int registrationRenewalWindowDays,
        @Min(value = 1, message = "O período mínimo de aviso de bloqueio é 1") @Max(value = 90, message = "O período máximo de aviso de bloqueio é 90")
        int registrationBlockWarningDays
) {
}
