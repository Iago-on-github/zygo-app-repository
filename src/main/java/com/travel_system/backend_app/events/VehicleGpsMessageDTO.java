package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;

public record VehicleGpsMessageDTO(
        String city,
        String travelId,
        VehicleLocationRequestDTO vehicleLocation) {
}
