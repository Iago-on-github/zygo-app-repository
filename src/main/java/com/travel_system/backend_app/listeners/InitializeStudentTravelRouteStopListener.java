package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.InitializeStudentTravelRouteStopEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InitializeStudentTravelRouteStopListener {

    private final RedisTrackingService redisTrackingService;

    public InitializeStudentTravelRouteStopListener(RedisTrackingService redisTrackingService) {
        this.redisTrackingService = redisTrackingService;
    }

    @EventListener
    public void handleInitializeStudentTravelRouteStop(InitializeStudentTravelRouteStopEvent event) {
        // armazena os dados de inicialização
        redisTrackingService.storeInitializeStudentTravelRouteStopData(event);
    }
}
