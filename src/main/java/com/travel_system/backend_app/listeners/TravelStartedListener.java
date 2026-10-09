package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.TravelStartedEvent;
import com.travel_system.backend_app.exceptions.TripNotFoundException;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.TravelCacheService;
import com.travel_system.backend_app.service.TravelNotificationService;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TravelStartedListener {

    private final TravelRepository travelRepository;

    private final RedisTemplate<String, String> redisTemplate;

    private final TravelNotificationService travelNotificationService;
    private final TravelCacheService travelCacheService;
    private final RedisTrackingService redisTrackingService;

    public TravelStartedListener(TravelRepository travelRepository, RedisTemplate<String, String> redisTemplate, TravelNotificationService travelNotificationService, TravelCacheService travelCacheService, RedisTrackingService redisTrackingService) {
        this.travelRepository = travelRepository;
        this.redisTemplate = redisTemplate;
        this.travelNotificationService = travelNotificationService;
        this.travelCacheService = travelCacheService;
        this.redisTrackingService = redisTrackingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    @Transactional(readOnly = true)
    public void onTravelStartedFirebase(TravelStartedEvent event) {
        // busca a viagem
        Travel travel = travelRepository.findById(event.travelId())
                .orElseThrow(() -> new TripNotFoundException("Viagem não encontrada"));

        // envia notificação para o firebase comunicando o incio da viagem
        travelNotificationService.sendTravelStartedNotification(travel);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTravelStartedRedis(TravelStartedEvent event) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // adiciona viagem ativa ao redis para métricas de self-health do sistema
            redisTrackingService.addActiveTravel(event.travelId());

            // limpa o cache estático da viagem (por ter mudado o STATUS da viagem)
            travelCacheService.invalidateTravelStaticCache(event.travelId());
            return null;
        });
    }

}
