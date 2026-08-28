package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.notifications.StudentProximityNotificationDTO;
import com.travel_system.backend_app.events.routestops_algorithm.CancelledStudentTravelRouteStopEvent;
import com.travel_system.backend_app.events.routestops_algorithm.InvalidStudentTravelRouteStopEvent;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.NotificationConstants.INVALID_ROUTE_LAST_NOTIFY_TIME;

/*
* notificações tracking da viagem
* */

@Service
public class TravelTrackingNotificationService {
    private static final Logger log = LoggerFactory.getLogger(TravelTrackingNotificationService.class);

    private final FirebaseNotificationSender firebaseNotificationSender;
    private final RedisNotificationService redisNotificationService;

    public TravelTrackingNotificationService(FirebaseNotificationSender firebaseNotificationSender, RedisNotificationService redisNotificationService) {
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.redisNotificationService = redisNotificationService;
    }

    // SLOW
    public void sendTrackingSlowMovementNotification(UUID travelId, UUID customerId, VelocityAnalysisDTO velocityAnalysis) {;
        MovementState movementState = velocityAnalysis.movementState();

        String title = "Alerta de ônibus lento";
        String message = "O ônibus está se movimentando lentamente. Fique atento.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "VEHICLE_SLOW",
                "travelId", travelId.toString(),
                "movementState", movementState.toString()
        );

        // send to firebase through handle
        handleMovementNotification(travelId, title, message, link, data, customerId);
    }

    // STOPPED
    public void sendTrackingStoppedMovementNotification(UUID travelId, UUID customerId, VelocityAnalysisDTO velocityAnalysis) {
        MovementState movementState = velocityAnalysis.movementState();

        String title = "Alerta de ônibus parado";
        String message = "O ônibus está parado, possíveis problemas na via. Fique atento.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "VEHICLE_STOPPED",
                "travelId", travelId.toString(),
                "movementState", movementState.toString()
        );

        // send to firebase through handle
        handleMovementNotification(travelId, title, message, link, data, customerId);
    }

    // AUTO DISCONNECTED
    public void sendAutoDisconnectStudentNotification(Travel travel, UUID studentId) {
        UUID travelId = travel.getId();
        UUID customerId = travel.getCustomerId();

        NotificationAudience student = NotificationAudience.SPECIFIC_USER; // estudante específico no qual foi desvinculado

        String title = "Desconexão automática";
        String message = "Você foi desembarcado automaticamente da viagem por estar distante do ônibus";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "AUTO_DISCONNECTED_STUDENT",
                "travelId", travelId.toString(),
                "studentStatus", "AUTO_DISCONNECTED"
        );

        // dto notificação estudante
        PushNotificationCommandDTO studentCommand = new PushNotificationCommandDTO(
                student, studentId, customerId, travelId, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(studentCommand);
    }

    // STUDENT NOT ASSOCIATED TO ROUTE_STOP
    public void sendNotAssociatedToRouteStopNotification(InvalidStudentTravelRouteStopEvent studentTravelRouteStopEvent) {
        UUID travelId = studentTravelRouteStopEvent.travelId();
        UUID studentId = studentTravelRouteStopEvent.studentId();
        UUID customerId = studentTravelRouteStopEvent.customerId();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = studentTravelRouteStopEvent.studentTravelRouteStopStatus();

        // manda somente para o user em questão
        NotificationAudience specificUser = NotificationAudience.SPECIFIC_USER;

        String title = "Viagem sem pontos de parada compatíveis";
        String message = "Seus Ponto(s) de Parada(s) não são compatíveis com os dessa Rota. Verifique se embarcou na viagem correta ou informe ao motorista.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "studentTravelRouteStopStatus", studentTravelRouteStopStatus.toString()
        );

        PushNotificationCommandDTO studentCommand = new PushNotificationCommandDTO(
                specificUser,
                studentId,
                customerId,
                travelId,
                title,
                message,
                link,
                Priority.NORMAL,
                data
        );

        // número de notificações enviadas para o usuário
        Integer countInvalidRouteNotifications = redisNotificationService.getCountInvalidRouteNotifications(travelId, studentId);

        // verifica se é a primeira notificação, envia e retorna direto
        if (countInvalidRouteNotifications == 0) {
            firebaseNotificationSender.sendPushNotification(studentCommand);

            // primeira notificação
            Instant notifyAt = Instant.now();
            int firstNotificationCount = 1;
            redisNotificationService.putInvalidRouteLastNotifyData(travelId, studentId, notifyAt, firstNotificationCount);

            return;
        }

        // se não for a primeira notificação, envia pushs por tempo padrão multiplicado a cada notificação enviada
        handleInvalidRouteNotification(travelId, studentId, countInvalidRouteNotifications, studentCommand);

    }

    // CANCELLED ROUTE_STOP_ALGORITHM
    public void sendCancelledRouteStopNotification(CancelledStudentTravelRouteStopEvent cancelledStudentTravelRouteStopEvent) {
        UUID travelId = cancelledStudentTravelRouteStopEvent.travelId();
        UUID studentId = cancelledStudentTravelRouteStopEvent.studentId();
        UUID customerId = cancelledStudentTravelRouteStopEvent.customerId();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = cancelledStudentTravelRouteStopEvent.studentTravelRouteStopStatus();

        // manda somente para o user em questão
        NotificationAudience specificUser = NotificationAudience.SPECIFIC_USER;

        String title = "Viagem cancelada.";
        String message = "Seu(s) Ponto(s) de Parada(s) não serão rastreados pois a viagem foi cancelada";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "studentTravelRouteStopStatus", studentTravelRouteStopStatus.toString()
        );

        PushNotificationCommandDTO studentCommand = new PushNotificationCommandDTO(
                specificUser,
                studentId,
                customerId,
                travelId,
                title,
                message,
                link,
                Priority.NORMAL,
                data
        );

        // envia notificação
        firebaseNotificationSender.sendPushNotification(studentCommand);
    }

    // STUDENT PROXIMITY ALERT (PUSHs)
    public void sendStudentProximityNotification(StudentProximityNotificationDTO proximityEvents) {
        Double distance = proximityEvents.distance();
        UUID travelId = proximityEvents.travelId();
        UUID studentId = proximityEvents.studentId();
        UUID customerId = proximityEvents.customerId();
        String zone = proximityEvents.zone();
        String alertType = proximityEvents.alertType();

        // manda somente para o user em questão
        NotificationAudience specificUser = NotificationAudience.SPECIFIC_USER;

        String title = "Alerta de Aproximação do Ônibus";
        String message = "O ônibus está a " + Math.round(distance) + " metros de você.";
        String link = "/travels/" + travelId + "/proximity/student/" + studentId;

        Map<String, String> data = Map.of(
                "eventType", "STUDENT_PROXIMITY",
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "distance", distance.toString(),
                "zone", zone,
                "alertType", alertType
        );

        PushNotificationCommandDTO studentCommand = new PushNotificationCommandDTO(
                specificUser,
                studentId,
                customerId,
                travelId,
                title,
                message,
                link,
                Priority.NORMAL,
                data
        );

        // envia notificação
        firebaseNotificationSender.sendPushNotification(studentCommand);
    }

    // decide quando enviar as notifications para InvalidRoute
    private void handleInvalidRouteNotification(UUID travelId, UUID studentId, int countInvalidRouteNotifications, PushNotificationCommandDTO studentCommand) {
        Long invalidRouteLastNotify = redisNotificationService.getInvalidRouteLastNotify(travelId, studentId);

        long timeNow = Instant.now().toEpochMilli();

        // verifica se existe timestamp no redis (representando o tempo da última notificação)
        if (invalidRouteLastNotify != null) {

            long totalTimeOfLastNotify = timeNow - invalidRouteLastNotify;

            // se tempo da última notificação passou do definido e só enviou no máximo UMA vez, manda notificação
            if (totalTimeOfLastNotify >= INVALID_ROUTE_LAST_NOTIFY_TIME && countInvalidRouteNotifications == 1) {
                log.info("[InvalidRoute - notify] - Segunda notificação enviada");

                firebaseNotificationSender.sendPushNotification(studentCommand);

                Instant notifyAt = Instant.now();
                countInvalidRouteNotifications += 1;

                redisNotificationService.putInvalidRouteLastNotifyData(travelId, studentId, notifyAt, countInvalidRouteNotifications);

                return;
            }

            int maxSentNotifications = 4;
            long multiplyNotificationTime = INVALID_ROUTE_LAST_NOTIFY_TIME * countInvalidRouteNotifications;

            /*
            * múltiplica o tempo de envio de cada notificação com base na quantidade já enviada
            * 1 = assim que entra na viagem | 2 = 5min | 3 = 15min | 4 = 20min
            * envia no máximo 4 notificações (0-3)
            * */
            if (totalTimeOfLastNotify >= multiplyNotificationTime && countInvalidRouteNotifications < maxSentNotifications) {
                log.info("[InvalidRoute - notify] - Notificação enviada de número {} enviada: ", countInvalidRouteNotifications);

                firebaseNotificationSender.sendPushNotification(studentCommand);

                Instant notifyAt = Instant.now();
                countInvalidRouteNotifications += 1;

                redisNotificationService.putInvalidRouteLastNotifyData(travelId, studentId, notifyAt, countInvalidRouteNotifications);
            }

        }
    }

    // handle movement notification
    private void handleMovementNotification(UUID travelId, String title, String message, String link, Map<String, String> data, UUID customerId) {
        NotificationAudience travelStudents = NotificationAudience.EMBARKED_TRAVEL_STUDENTS; // envia para os estudantes embarcados na viagem

        // dto notificação estudantes
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(
                travelStudents, null, customerId, travelId, title, message, link, Priority.HIGH, data);

        // envia as notificações
        firebaseNotificationSender.sendPushNotification(students);
    }
}
