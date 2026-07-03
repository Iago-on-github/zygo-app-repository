package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.VehicleMovementEvents;
import com.travel_system.backend_app.service.AsyncNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VehicleMovementListener {

    private final AsyncNotificationService asyncNotificationService;

    public VehicleMovementListener(AsyncNotificationService asyncNotificationService) {
        this.asyncNotificationService = asyncNotificationService;
    }

    @Async
    @EventListener
    public void handleVehicleMovementEvents(VehicleMovementEvents vehicleMovementEvents) {
        asyncNotificationService.processNotificationType(
                vehicleMovementEvents.travelId(),
                vehicleMovementEvents.velocityAnalysis(),
                vehicleMovementEvents.decision());
    }
}
