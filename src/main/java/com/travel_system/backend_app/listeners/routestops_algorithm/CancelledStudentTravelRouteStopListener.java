package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.CancelledStudentTravelRouteStopEvent;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import com.travel_system.backend_app.service.TravelTrackingNotificationService;
import com.travel_system.backend_app.service.TravelTrackingStaticCacheService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class CancelledStudentTravelRouteStopListener {

    private final TravelTrackingStaticCacheService travelTrackingStaticCacheService;
    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;
    private final TravelTrackingNotificationService travelTrackingNotificationService;

    public CancelledStudentTravelRouteStopListener(TravelTrackingStaticCacheService travelTrackingStaticCacheService, StudentTravelRouteStopRepository studentTravelRouteStopRepository, TravelTrackingNotificationService travelTrackingNotificationService) {
        this.travelTrackingStaticCacheService = travelTrackingStaticCacheService;
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
        this.travelTrackingNotificationService = travelTrackingNotificationService;
    }

    @EventListener
    @Async
    public void handleCancelledStudentTravelRouteStop(CancelledStudentTravelRouteStopEvent event) {
        studentTravelRouteStopRepository.updateCancelledStatus(event.studentTravelId(), event.routeStopId(), event.studentTravelRouteStopStatus(), event.lastValidatedAt());

        // notificação
        travelTrackingNotificationService.sendCancelledRouteStopNotification(event);

        // limpa o cache estático do tracking da viagem p/ o estudante
        travelTrackingStaticCacheService.removeStudentTravelTrackingCache(event.travelId(), event.studentTravelId());
    }
}
