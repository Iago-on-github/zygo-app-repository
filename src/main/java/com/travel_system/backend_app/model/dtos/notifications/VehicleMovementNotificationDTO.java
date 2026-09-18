package com.travel_system.backend_app.model.dtos.notifications;

import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.enums.ShouldNotify;

import java.util.UUID;

public record VehicleMovementNotificationDTO(UUID travelId,
                                             VelocityAnalysisDTO velocityAnalysis,
                                             ShouldNotify decision,
                                             UUID traceId) {
}
