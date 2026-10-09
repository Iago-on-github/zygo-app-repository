package com.travel_system.backend_app.model.dtos.security;

import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record SetupAuthProcessDTO(@NotBlank String password, @NotNull SensitiveOperationType type) {
}
