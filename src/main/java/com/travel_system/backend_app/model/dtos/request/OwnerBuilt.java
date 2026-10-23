package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.OwnerType;

import java.util.UUID;

public record OwnerBuilt(
        OwnerType type,
        UUID id

) {
}
