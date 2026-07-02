package com.travel_system.backend_app.model.dtos;

import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.Priority;

import java.util.UUID;

public record MovementNotificationEventDTO(UUID userId,
                                           UUID travelId,
                                           MovementState movementState,
                                           Priority priority,
                                           String message,
                                           UUID traceId) {
}
