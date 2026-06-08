package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;

import java.util.UUID;

public record StudentAwayStateCheckEvent(UUID travelId, LiveLocationDTO liveLocationDTO) {
}
