package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.ProcessStudentTravelRouteStopApproachingEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.StudentTravelRouteStopService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProcessStudentTravelRouteStopApproachingListener {
    private final RedisTrackingService redisTrackingService;

    public ProcessStudentTravelRouteStopApproachingListener(RedisTrackingService redisTrackingService) {
        this.redisTrackingService = redisTrackingService;
    }

    @EventListener
    public void handleProcessRouteStopApproaching(ProcessStudentTravelRouteStopApproachingEvent event) {
        redisTrackingService.updateStudentTravelRouteStopProcessMonitoring(event);
    }
}
