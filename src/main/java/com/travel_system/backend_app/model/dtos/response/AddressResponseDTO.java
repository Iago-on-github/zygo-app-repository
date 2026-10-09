package com.travel_system.backend_app.model.dtos.response;

import java.util.UUID;

public record AddressResponseDTO(
        UUID id,
        String street,
        Integer number,
        String neighborhood,
        String city,
        String cep,
        String complement
) {
}
