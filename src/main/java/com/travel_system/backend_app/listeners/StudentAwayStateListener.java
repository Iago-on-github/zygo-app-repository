package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.service.LocationService;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StudentAwayStateListener {

    private final LocationService locationService;
    private final RedisTrackingService redisTrackingService;

    private final Logger logger = LoggerFactory.getLogger(StudentAwayStateListener.class);

    public StudentAwayStateListener(LocationService locationService, RedisTrackingService redisTrackingService) {
        this.locationService = locationService;
        this.redisTrackingService = redisTrackingService;
    }

    @Async("studentAwayTaskExecutor")
    @EventListener
    public void handleStudentAwayState(StudentAwayStateCheckEvent event) {
        long start = System.currentTimeMillis(); // usado para logs de TTL

        // usa redis para garantir que a demora na execução do algoritmo não gere múltiplos processamentos, evitando duplicidade
        boolean isLockAcquire = redisTrackingService.tryAcquireStudentAwayStateLock(event.travelId());

        if (!isLockAcquire) {
            logger.debug("[handleStudentAwayState] viagem {} já está sendo processada.", event.travelId());
            return;
        }
        try {
            locationService.processStudentAwayState(event);

        } catch (Exception e) {
            logger.error("[handleStudentAwayState] - não foi possível publicar o evento para a viagem: {}", event.travelId(), e);
        } finally {
            long elapsed = System.currentTimeMillis() - start;

            logger.info("[processStudentAwayState] a viagem {} foi processada em {} ms", event.travelId(), elapsed);

            redisTrackingService.releaseStudentAwayStateLock(event.travelId());
        }
    }
}
