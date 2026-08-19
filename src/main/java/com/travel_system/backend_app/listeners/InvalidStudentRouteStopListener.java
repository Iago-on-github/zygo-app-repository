package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.InvalidStudentTravelRouteStopEvent;
import com.travel_system.backend_app.service.TravelTrackingNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InvalidStudentRouteStopListener {
    private final TravelTrackingNotificationService trackingNotificationService;

    public InvalidStudentRouteStopListener(TravelTrackingNotificationService trackingNotificationService) {
        this.trackingNotificationService = trackingNotificationService;
    }

    @EventListener
    public void handleInvalidStudentTravelRouteStop(InvalidStudentTravelRouteStopEvent event) {
        trackingNotificationService.sendNotAssociatedToRouteStopNotification(event);

        // inserção de validação de estado e registro histórico
    }


}
