package com.travel_system.backend_app.events;

import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

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
