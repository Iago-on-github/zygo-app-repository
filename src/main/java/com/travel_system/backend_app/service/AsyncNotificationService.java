package com.travel_system.backend_app.service;

import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.MovementNotificationEventDTO;
import com.travel_system.backend_app.model.dtos.VehicleMovementNotificationDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@EnableAsync
public class AsyncNotificationService {

    private final RedisTrackingService redisTrackingService;
    private final TravelTrackingNotificationService trackingNotificationService;

    private final StudentTravelRepository studentTravelRepository;
    private final TravelRepository travelRepository;

    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationService.class);

    public AsyncNotificationService(RedisTrackingService redisTrackingService, TravelTrackingNotificationService trackingNotificationService, StudentTravelRepository studentTravelRepository, TravelRepository travelRepository) {
        this.redisTrackingService = redisTrackingService;
        this.trackingNotificationService = trackingNotificationService;
        this.studentTravelRepository = studentTravelRepository;
        this.travelRepository = travelRepository;
    }

    @Async(value = "notificationTaskExecutor")
    public void processNotificationType(UUID travelId, VelocityAnalysisDTO velocityAnalysis, ShouldNotify shouldNotify) {
        if (shouldNotify.equals(ShouldNotify.SHOULD_NO_NOTIFY)) return;

        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_SLOW)) {
            logger.info("Enviando notificação para ônibus lento... {} {}", travelId, shouldNotify);
            slowNotification(travelId, velocityAnalysis);
        }
        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_STOPPED)) {
            logger.info("Enviando notificação para ônibus parado... {} {}", travelId, shouldNotify);
            stoppedNotification(travelId, velocityAnalysis);
        }
    }

    /*
    * envia notificação quando o ônibus estiver LENTO (slow)
    * deve marcar corretamente no redis quando a notificação foi enviada
    * */
    private void slowNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis) {
        if (velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados da viagem inválidos ou corrompidos");

        Travel travel = travelRepository.findById(travelId).orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada: " + travelId));

        if (!velocityAnalysis.movementState().equals(MovementState.SLOW)) return;

        // controlar cooldawn da notificação
        redisTrackingService.markNotificationAsSent(travelId);

        // envia notificação para o firebase
        trackingNotificationService.sendTrackingSlowMovementNotification(travel, velocityAnalysis);
    }

    /*
     * envia notificação quando o ônibus estiver PARADO (STOPPED)
     * deve marcar corretamente no redis quando a notificação foi enviada
     * */
    private void stoppedNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis) {
        if (travelId == null || velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados de viagem inválidos corrompidos.");

        Travel travel = travelRepository.findById(travelId).orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada: " + travelId));

        if (!velocityAnalysis.movementState().equals(MovementState.STOPPED)) return;

        // controlar cooldawn da notificação
        redisTrackingService.markNotificationAsSent(travelId);

        // envia notificação para o firebase
        trackingNotificationService.sendTrackingStoppedMovementNotification(travel, velocityAnalysis);
    }
}
