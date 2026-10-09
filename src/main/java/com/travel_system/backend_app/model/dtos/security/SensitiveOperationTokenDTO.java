package com.travel_system.backend_app.model.dtos.security;

import javax.validation.constraints.NotBlank;

public record SensitiveOperationTokenDTO(@NotBlank String token) {
}
