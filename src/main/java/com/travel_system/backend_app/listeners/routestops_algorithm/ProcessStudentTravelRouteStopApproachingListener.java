package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.ProcessStudentTravelRouteStopApproachingEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProcessStudentTravelRouteStopApproachingListener {
    private final Logger log = LoggerFactory.getLogger(ProcessStudentTravelRouteStopApproachingListener.class);


    private final RedisTrackingService redisTrackingService;

    public ProcessStudentTravelRouteStopApproachingListener(RedisTrackingService redisTrackingService) {
        this.redisTrackingService = redisTrackingService;
    }

    @EventListener
    @Async("routeStopApproachTaskExecutor")
    public void handleProcessRouteStopApproaching(ProcessStudentTravelRouteStopApproachingEvent event) {
        if (event == null) {
            log.warn("[handleProcessRouteStopApproaching] - Evento nulo recebido, ignorando processamento");
            return;
        }

        redisTrackingService.updateStudentTravelRouteStopProcessMonitoring(event);
    }
}
