package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import java.util.UUID;

// // dto com dados simples do Ponto de Parada
public record RouteStopSimpleResponseDTO(
        UUID id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        GeneralStatus status
) {
}
