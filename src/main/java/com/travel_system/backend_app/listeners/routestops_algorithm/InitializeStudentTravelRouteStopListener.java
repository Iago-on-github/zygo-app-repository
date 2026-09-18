package com.travel_system.backend_app.listeners.routestops_algorithm;

import com.travel_system.backend_app.events.routestops_algorithm.InitializeStudentTravelRouteStopEvent;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InitializeStudentTravelRouteStopListener {
    private final Logger log = LoggerFactory.getLogger(InitializeStudentTravelRouteStopListener.class);


    private final RedisTrackingService redisTrackingService;

    public InitializeStudentTravelRouteStopListener(RedisTrackingService redisTrackingService) {
        this.redisTrackingService = redisTrackingService;
    }

    @EventListener
    public void handleInitializeStudentTravelRouteStop(InitializeStudentTravelRouteStopEvent event) {
        if (event == null) {
            log.warn("[handleInitializeStudentTravelRouteStop] - Evento nulo recebido, ignorando processamento");
            return;
        }

        // armazena os dados de inicialização
        redisTrackingService.storeInitializeStudentTravelRouteStopData(event);
    }
}
