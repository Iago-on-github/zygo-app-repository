package com.travel_system.backend_app.events.routestops_algorithm;

/*
* dto de evento para aproximação do ponto de parada
* */

import java.time.Instant;
import java.util.UUID;

public record ProcessStudentTravelRouteStopApproachingEvent(
        UUID studentTravelId,
        UUID studentId,
        UUID travelId,
        UUID routeStopId,
        Double distance,
        Instant occurredAt
) {
}
