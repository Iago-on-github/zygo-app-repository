package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.ConfirmStudentTravelRouteStopReachedEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.TravelTrackingStaticCacheService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfirmStudentTravelRouteStopListener {
    private final RedisTrackingService redisTrackingService;
    private final TravelTrackingStaticCacheService travelTrackingStaticCacheService;

    public ConfirmStudentTravelRouteStopListener(RedisTrackingService redisTrackingService, TravelTrackingStaticCacheService travelTrackingStaticCacheService) {
        this.redisTrackingService = redisTrackingService;
        this.travelTrackingStaticCacheService = travelTrackingStaticCacheService;
    }

    @EventListener
    public void handleConfirmStudentTravelRouteStop(ConfirmStudentTravelRouteStopReachedEvent event) {
        redisTrackingService.updateStudentTravelRouteStopConfirmMonitoring(event);

        // limpa cache
        travelTrackingStaticCacheService.removeStudentTravelTrackingCache(event.travelId(), event.studentTravelId());

        // limpa o redis para o contexto do algoritmo de proximidade do routestop
        redisTrackingService.deleteStudentTravelRouteStopMonitoring(event.travelId(), event.studentTravelId());
    }
}
