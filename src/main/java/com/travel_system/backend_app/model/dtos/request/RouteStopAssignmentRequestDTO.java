package com.travel_system.backend_app.model.dtos.request;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RouteStopAssignmentRequestDTO(
        @NotNull(message = "O ID da parada é obrigatório")
        UUID routeStopId,
        @NotNull(message = "A ordem de parada é obrigatória")
        Integer stopSequence,
        @NotNull(message = "Obrigatório marcar se é Opcional a parada")
        boolean isOptionalStop
) {
}
