package com.travel_system.backend_app.model.dtos;

import com.travel_system.backend_app.model.enums.MovementState;

import java.time.Instant;

public record AnalyzeMovementStateDTO(MovementState movementState,
                                      Instant stateStartedAt,
                                      Instant lastNotificationSendAt,
                                      Instant lastEtaNotificationAt
                                      ) {
}
