package com.travel_system.backend_app.model.dtos.mapboxApi;

import java.util.List;

public record RoutesDTO(
        String weight_name,
        Double weight,
        Double duration,
        Double distance,
        List<LegsDTO> legs,
        String geometry
) {
}
