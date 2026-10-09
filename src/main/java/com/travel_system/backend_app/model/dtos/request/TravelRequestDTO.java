package com.travel_system.backend_app.model.dtos.request;



import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.UUID;

public record TravelRequestDTO(
        @NotNull(message = "A Rota Padrão é obrigatória")
        UUID standardRouteId,
        @NotNull(message = "O veículo é obrigatório")
        UUID vehicleId,
        @NotNull(message = "O período é obrigatório")
        TravelPeriod travelPeriod,
        @NotNull(message = "A direção é obrigatória")
        TravelDirection travelDirection,
        @NotNull(message = "A coordenada de Longitude de ORIGEM é obrigatória")
        Double originLongitude,
        @NotNull(message = "A coordenada de Latitude de ORIGEM é obrigatória")
        Double originLatitude,
        @NotNull(message = "A coordenada de Longitude de DESTINO é obrigatória")
        Double finalLongitude,
        @NotNull(message = "A coordenada de Latitude de DESTINO é obrigatória")
        Double finalLatitude,
        @NotNull(message = "A cidade de destino é obrigatório")
        @Size(max = 20)
        String destinationCity) {
}
