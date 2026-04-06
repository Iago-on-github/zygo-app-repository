package com.travel_system.backend_app.model.dtos.mapboxApi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RouteDetailsDTO(
        @JsonProperty("duration") Double duration,
        @JsonProperty("distance") Double distance,
        @JsonProperty("geometry") String geometry
) {
}
