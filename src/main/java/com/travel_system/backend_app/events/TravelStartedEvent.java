package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.Travel;

import java.util.UUID;

public record TravelStartedEvent(
        UUID travelId

) {
}
