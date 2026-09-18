package com.travel_system.backend_app.model.dtos.mapboxApi;

import java.util.List;

public record LegsDTO(
        List<Object> via_waypoints,
        List<AdminDTO> admins,
        Double weight,
        Double duration,
        List<Object> steps,
        Double distance,
        String summary
) {
}
