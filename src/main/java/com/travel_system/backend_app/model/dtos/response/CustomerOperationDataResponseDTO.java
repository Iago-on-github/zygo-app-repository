package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerOperationDataResponseDTO(
        UUID id,
        String name,
        String slug,
        String legalName,
        String cnpj,
        String contactEmail,
        String contactTelephone,
        GeneralStatus status,
        String cityName,
        ClientSector clientSector,
        String logoUrl,
        AddressResponseDTO addressResponse,
        CustomerPlan plan,
        Instant createdAt,
        Instant updatedAt
        ) {
}
