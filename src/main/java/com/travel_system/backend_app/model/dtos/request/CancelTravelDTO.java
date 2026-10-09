package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.UUID;

public record CancelTravelDTO(
        @NotNull
        UUID travelId,
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String cancelledReason
) {
}
