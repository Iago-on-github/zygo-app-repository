package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.Set;
import java.util.UUID;

// dto com dados simples da Rota Padrão
public record StandardRouteSimpleResponseDTO(
        UUID id,
        String routeName,
        String routeDescription,
        Set<TravelPeriod> travelPeriods,
        GeneralStatus status
) {
}
