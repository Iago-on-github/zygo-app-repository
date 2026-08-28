package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.notifications.StudentProximityNotificationDTO;
import com.travel_system.backend_app.model.dtos.notifications.VehicleMovementNotificationDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@EnableAsync
public class AsyncNotificationService {

    private final RedisTrackingService redisTrackingService;
    private final TravelTrackingNotificationService trackingNotificationService;
    private final TravelCacheService travelCacheService;

    private final StudentTravelRepository studentTravelRepository;
    private final TravelRepository travelRepository;

    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationService.class);

    public AsyncNotificationService(RedisTrackingService redisTrackingService, TravelTrackingNotificationService trackingNotificationService, TravelCacheService travelCacheService, StudentTravelRepository studentTravelRepository, TravelRepository travelRepository) {
        this.redisTrackingService = redisTrackingService;
        this.trackingNotificationService = trackingNotificationService;
        this.travelCacheService = travelCacheService;
        this.studentTravelRepository = studentTravelRepository;
        this.travelRepository = travelRepository;
    }

    // mudar nome para "processBusVelocityNotificationType"
    @Async(value = "notificationTaskExecutor")
    public void processNotificationType(VehicleMovementNotificationDTO vehicleMovementNotificationDTO, ShouldNotify shouldNotify) {
        UUID travelId = vehicleMovementNotificationDTO.travelId();

        // busca cache estático da viagem para conseguir dados como customerId
        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        UUID customerId = travelStaticCache.customerId();
        VelocityAnalysisDTO velocityAnalysis = vehicleMovementNotificationDTO.velocityAnalysis();

        if (shouldNotify.equals(ShouldNotify.SHOULD_NO_NOTIFY)) return;

        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_SLOW)) {
            logger.info("Enviando notificação para ônibus lento... {}", shouldNotify);
            slowNotification(travelId, customerId, velocityAnalysis);
        }
        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_STOPPED)) {
            logger.info("Enviando notificação para ônibus parado... {}", shouldNotify);
            stoppedNotification(travelId, customerId, velocityAnalysis);
        }
    }

    /*
    * gera notificações pushs de proximidade do ônibus p/ o aluno
    * */
    public void processStudentProximity(StudentProximityNotificationDTO studentProximityNotificationDTO) {
        trackingNotificationService.sendStudentProximityNotification(studentProximityNotificationDTO);
    }

    /*
    * envia notificação quando o ônibus estiver LENTO (slow)
    * deve marcar corretamente no redis quando a notificação foi enviada
    * */
    private void slowNotification(UUID travelId, UUID customerId, VelocityAnalysisDTO velocityAnalysis) {
        if (velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados da viagem inválidos ou corrompidos");

        if (!velocityAnalysis.movementState().equals(MovementState.SLOW)) return;

        // controlar cooldawn da notificação
        redisTrackingService.markNotificationAsSent(travelId);

        // envia notificação para o firebase
        trackingNotificationService.sendTrackingSlowMovementNotification(travelId, customerId, velocityAnalysis);
    }

    /*
     * envia notificação quando o ônibus estiver PARADO (STOPPED)
     * deve marcar corretamente no redis quando a notificação foi enviada
     * */
    private void stoppedNotification(UUID travelId, UUID customerId, VelocityAnalysisDTO velocityAnalysis) {
        if (velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados de viagem inválidos corrompidos.");

        if (!velocityAnalysis.movementState().equals(MovementState.STOPPED)) return;

        // controlar cooldawn da notificação
        redisTrackingService.markNotificationAsSent(travelId);

        // envia notificação para o firebase
        trackingNotificationService.sendTrackingStoppedMovementNotification(travelId, customerId, velocityAnalysis);
    }
}
