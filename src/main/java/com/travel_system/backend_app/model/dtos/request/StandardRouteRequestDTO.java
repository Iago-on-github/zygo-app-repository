package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.TravelPeriod;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record StandardRouteRequestDTO(
        @NotNull @NotBlank(message = "O nome da rota é obrigatório")
        String routeName,
        @NotBlank(message = "A descrição da rota é obrigatória")
        String routeDescription,
        @NotNull(message = "A latitude de origem é obrigatória")
        Double originLatitude,
        @NotNull(message = "A longitude de origem é obrigatória")
        Double originLongitude,
        @NotNull(message = "A latitude de destino é obrigatória")
        Double destinationLatitude,
        @NotNull(message = "A longitude de destino é obrigatória")
        Double destinationLongitude,
        @NotNull(message = "O período de viagem é obrigatório")
        Set<TravelPeriod> periods,
        @NotEmpty(message = "A lista de paradas não pode ser nula nem vazia")
        Set<@Valid @NotNull RouteStopAssignmentRequestDTO> routeStops
) {
}
