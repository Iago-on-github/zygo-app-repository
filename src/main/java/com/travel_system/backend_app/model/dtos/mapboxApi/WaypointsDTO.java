package com.travel_system.backend_app.model.dtos.mapboxApi;

import java.util.List;

public record WaypointsDTO(
        Double distance,
        String name,
        List<Double> location
) {}
