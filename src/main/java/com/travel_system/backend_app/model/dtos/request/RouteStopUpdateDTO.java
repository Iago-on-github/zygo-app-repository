package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record RouteStopUpdateDTO(
        String name,
        String description,
        Double latitude,
        Double longitude
) {
}
