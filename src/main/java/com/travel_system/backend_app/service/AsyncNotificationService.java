package com.travel_system.backend_app.service;

import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.MovementNotificationEventDTO;
import com.travel_system.backend_app.model.dtos.VehicleMovementNotificationDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
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
    private final FirebaseNotificationSender firebaseNotificationSender;
    private final StudentTravelRepository studentTravelRepository;

    private static final Logger logger = LoggerFactory.getLogger(AsyncNotificationService.class);

    public AsyncNotificationService(RedisTrackingService redisTrackingService, FirebaseNotificationSender firebaseNotificationSender, StudentTravelRepository studentTravelRepository) {
        this.redisTrackingService = redisTrackingService;
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.studentTravelRepository = studentTravelRepository;
    }

    @Async(value = "notificationTaskExecutor")
    public void processNotificationType(UUID travelId, VelocityAnalysisDTO velocityAnalysis, ShouldNotify shouldNotify, UUID traceId) {
        if (shouldNotify.equals(ShouldNotify.SHOULD_NO_NOTIFY)) return;

        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_SLOW)) {
            logger.info("Enviando notificação para ônibus lento... {} {} {}", travelId, shouldNotify, traceId);
            slowNotification(travelId, velocityAnalysis, traceId);
        }
        if (shouldNotify.equals(ShouldNotify.SHOULD_NOTIFY_STOPPED)) {
            logger.info("Enviando notificação para ônibus parado... {} {} {}", travelId, shouldNotify, traceId);
            stoppedNotification(travelId, velocityAnalysis, traceId);
        }
    }

    /*
    * envia notificação quando o ônibus estiver LENTO (slow)
    * deve marcar corretamente no redis quando a notificação foi enviada
    * */
    private void slowNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis, UUID traceId) {
        if (travelId == null || velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados da viagem inválidos ou corrompidos");

        if (!velocityAnalysis.movementState().equals(MovementState.SLOW)) return;

        // recuperar infos e preparar dto de envio ao firebase
        MovementState movementState = velocityAnalysis.movementState();
        Instant now = Instant.now();
        final String message = "Alerta de ônibus LENTO. Fique atento.";
        Priority priority = Priority.NORMAL;

        // controlar cooldawn
        redisTrackingService.markNotificationAsSent(String.valueOf(travelId));

        List<UUID> studentsAtTrip = studentTravelRepository.findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId);

        studentsAtTrip.forEach(studentId -> {
            try {
                // enviar notificação para cada estudante
                firebaseNotificationSender.pushNotificationToFirebase(new MovementNotificationEventDTO(studentId, travelId, movementState, priority, message, traceId));
            } catch (Exception e) {
                logger.error("[slowNotification] Falha no envio de notificação para o aluno: {} {}", studentId, e.getMessage());
            }
        });
    }

    /*
     * envia notificação quando o ônibus estiver PARADO (STOPPED)
     * deve marcar corretamente no redis quando a notificação foi enviada
     * */
    private void stoppedNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis, UUID traceId) {
        if (travelId == null || velocityAnalysis == null) throw new EtaDataStatesInvalidException("Dados de viagem inválidos corrompidos.");

        if (!velocityAnalysis.movementState().equals(MovementState.STOPPED)) return;

        MovementState movementState = velocityAnalysis.movementState();
        Instant now = Instant.now();
        final String message = "Alerta de ônibus PARADO. Fique atento.";
        Priority priority = Priority.NORMAL;

        // controlar cooldawn
        redisTrackingService.markNotificationAsSent(String.valueOf(travelId));

        List<UUID> studentsAtTrip = studentTravelRepository.findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId);

        studentsAtTrip.forEach(studentId -> {
            try {
                // enviar notificação para cada estudante
                firebaseNotificationSender.pushNotificationToFirebase(new MovementNotificationEventDTO(studentId, travelId, movementState, priority, message, traceId));
            } catch (Exception e) {
                logger.error("[stoppedNotification] Falha no envio de notificação para o aluno: {} {}", studentId, e.getMessage());
            }
        });
    }
}
