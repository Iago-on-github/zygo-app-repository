package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.ConfirmStudentTravelRouteStopReachedEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfirmStudentTravelRouteStopListener {
    private final RedisTrackingService redisTrackingService;

    public ConfirmStudentTravelRouteStopListener(RedisTrackingService redisTrackingService) {
        this.redisTrackingService = redisTrackingService;
    }

    @EventListener
    public void handleConfirmStudentTravelRouteStop(ConfirmStudentTravelRouteStopReachedEvent event) {
        redisTrackingService.updateStudentTravelRouteStopConfirmMonitoring(event);
    }
}
