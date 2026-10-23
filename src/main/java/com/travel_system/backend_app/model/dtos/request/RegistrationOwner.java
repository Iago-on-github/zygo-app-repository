package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

public record RegistrationOwner(
        OwnerType type,
        UUID id
) {
}
