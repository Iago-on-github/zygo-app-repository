package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.InvalidStudentTravelRouteStopEvent;
import com.travel_system.backend_app.service.TravelTrackingNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InvalidTravelStudentRouteStopListener {
    private final Logger log = LoggerFactory.getLogger(InvalidTravelStudentRouteStopListener.class);

    private final TravelTrackingNotificationService trackingNotificationService;

    public InvalidTravelStudentRouteStopListener(TravelTrackingNotificationService trackingNotificationService) {
        this.trackingNotificationService = trackingNotificationService;
    }

    @EventListener
    public void handleInvalidStudentTravelRouteStop(InvalidStudentTravelRouteStopEvent event) {
        if (event == null) {
            log.warn("[handleInvalidStudentTravelRouteStop] - Evento nulo recebido, ignorando processamento");
            return;
        }

        trackingNotificationService.sendNotAssociatedToRouteStopNotification(event);

        // inserção de validação de estado e registro histórico
    }


}
