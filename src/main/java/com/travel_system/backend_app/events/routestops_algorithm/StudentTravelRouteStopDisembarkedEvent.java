package com.travel_system.backend_app.events.routestops_algorithm;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;

import java.time.Instant;
import java.util.UUID;

/*
* dto p/ publicar evento onde o estudante chegou ao ponto de parada e se desvinculou/foi desvinculado com sucesso + persistência SQL
* */

public record StudentTravelRouteStopDisembarkedEvent(
        UUID studentTravelId,
        UUID routeStopId,
        StudentTravelRouteStopStatus studentTravelRouteStopStatus,
        Instant lastValidatedAt,
        Instant reachedAt
) {
}
