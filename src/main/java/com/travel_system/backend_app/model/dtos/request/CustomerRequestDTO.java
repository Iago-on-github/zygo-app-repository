package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.enums.ClientSector;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record CustomerRequestDTO(
        @NotNull(message = "name is required")
        String name,
        @NotNull(message = "slug is required")
        String slug,
        @NotNull(message = "cnpj is required")
        String cnpj,
        @NotNull(message = "cityId is required")
        UUID cityId,
        Set<UUID> userIds,
        @NotNull(message = "clientSector is required")
        ClientSector clientSector,
        String profilePicture) {
}
