package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.annotations.ValidTimeZone;
import jakarta.validation.constraints.NotNull;

public record TimeZoneUpdateDTO(
        @NotNull
        @ValidTimeZone
        String timeZone
) {
}
