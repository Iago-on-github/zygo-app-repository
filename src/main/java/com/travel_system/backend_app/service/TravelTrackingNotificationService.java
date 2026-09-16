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
import static com.travel_system.backend_app.config.constants.NotificationConstants.MAX_SENT_NOT_ASSOCIATED_ROUTE_STOP_NOTIFICATIONS;

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

    /*
    * notificação de ônibus lento (slow state)
    * - envia apenas para os estudantes que estão embarcados na viagem
    * */
    public void sendTrackingSlowMovementNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis) {
        NotificationAudience travelStudents = NotificationAudience.EMBARKED_TRAVEL_STUDENTS; // envia para os estudantes embarcados na viagem

        MovementState movementState = velocityAnalysis.movementState();

        String title = "Alerta de ônibus lento";
        String message = "O ônibus está se movimentando lentamente. Fique atento.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "VEHICLE_SLOW",
                "travelId", travelId.toString(),
                "movementState", movementState.toString()
        );

        // estudantes
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(
                travelStudents, null, travelId, null, null, null, title, message, link, Priority.HIGH, data);

        // envia as notificações
        firebaseNotificationSender.sendPushNotification(students);
    }

    /*
     * notificação de ônibus parado no trânsito (stopped state)
     * - envia apenas para os estudantes que estão embarcados na viagem
     * */
    public void sendTrackingStoppedMovementNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis) {
        NotificationAudience travelStudents = NotificationAudience.EMBARKED_TRAVEL_STUDENTS; // envia para os estudantes embarcados na viagem

        MovementState movementState = velocityAnalysis.movementState();

        String title = "Alerta de ônibus parado";
        String message = "O ônibus está parado, possíveis problemas na via. Fique atento.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "VEHICLE_STOPPED",
                "travelId", travelId.toString(),
                "movementState", movementState.toString()
        );

        // estudantes
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(
                travelStudents, null, travelId, null, null, null, title, message, link, Priority.HIGH, data);

        // envia as notificações
        firebaseNotificationSender.sendPushNotification(students);
    }

    /*
    * auto desconexão (via zyggo algorithm):
    * - envia para o aluno específico no qual foi descnectado
    * - envia para o responsável vinculado a ele
    * */
    public void sendAutoDisconnectStudentNotification(Travel travel, UUID studentId) {
        NotificationAudience specificStudent = NotificationAudience.SPECIFIC_STUDENT; // estudante específico no qual foi desvinculado
        NotificationAudience studentResponsible = NotificationAudience.STUDENT_RESPONSIBLE;

        UUID travelId = travel.getId();

        String title = "Desconexão automática";
        String message = "Desembarque automático da viagem por estar muito distante do ônibus.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "AUTO_DISCONNECTED_STUDENT",
                "travelId", travelId.toString(),
                "studentStatus", "AUTO_DISCONNECTED"
        );

        // estudante
        PushNotificationCommandDTO studentCommand = new PushNotificationCommandDTO(
                specificStudent, null, travelId, studentId, null, null, title, message, link, Priority.NORMAL, data);

        // responsible
        PushNotificationCommandDTO responsibleCommand = new PushNotificationCommandDTO(
                studentResponsible, null,  travelId, studentId, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(studentCommand);
        firebaseNotificationSender.sendPushNotification(responsibleCommand);
    }

    /*
    * estudante não associado a um RouteStop naquela viagem
    * - envia apenas para o estudante específico
    * */
    public void sendNotAssociatedToRouteStopNotification(InvalidStudentTravelRouteStopEvent studentTravelRouteStopEvent) {
        NotificationAudience specificUser = NotificationAudience.SPECIFIC_STUDENT; // manda somente para o estudante em questão

        UUID travelId = studentTravelRouteStopEvent.travelId();
        UUID studentId = studentTravelRouteStopEvent.studentId();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = studentTravelRouteStopEvent.studentTravelRouteStopStatus();

        String title = "Viagem sem pontos de parada compatíveis";
        String message = "Seus Ponto(s) de Parada(s) não são compatíveis com os dessa Rota. Verifique se embarcou na viagem correta ou informe ao motorista da viagem.";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "studentTravelRouteStopStatus", studentTravelRouteStopStatus.toString()
        );

        PushNotificationCommandDTO studentCommand =
                new PushNotificationCommandDTO(specificUser, null, travelId, studentId, null, null, title, message, link, Priority.NORMAL, data
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

    /*
     * cancelamento de viagem: informa não rastreamento dos pontos de parada
     * - envia apenas para o estudante específico
     * */
    public void sendCancelledRouteStopNotification(CancelledStudentTravelRouteStopEvent cancelledStudentTravelRouteStopEvent) {
        UUID travelId = cancelledStudentTravelRouteStopEvent.travelId();
        UUID studentId = cancelledStudentTravelRouteStopEvent.studentId();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = cancelledStudentTravelRouteStopEvent.studentTravelRouteStopStatus();

        // manda somente para o user em questão
        NotificationAudience specificStudent = NotificationAudience.SPECIFIC_STUDENT;

        String title = "Viagem cancelada.";
        String message = "Seu(s) Ponto(s) de Parada(s) não serão rastreados pois a viagem foi cancelada";
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "studentTravelRouteStopStatus", studentTravelRouteStopStatus.toString()
        );

        PushNotificationCommandDTO studentCommand =
                new PushNotificationCommandDTO(specificStudent, null, travelId, studentId, null, null, title, message, link, Priority.NORMAL, data);

        // envia notificação
        firebaseNotificationSender.sendPushNotification(studentCommand);
    }

    /*
     * pushs de notificação informando a distância >ônibus< para com >estudante<
     * - envia apenas para o estudante específico
     * */
    public void sendStudentProximityNotification(StudentProximityNotificationDTO proximityEvents) {
        Double distance = proximityEvents.distance();
        UUID travelId = proximityEvents.travelId();
        UUID studentId = proximityEvents.studentId();
        String zone = proximityEvents.zone();
        String alertType = proximityEvents.alertType();

        // manda somente para o estudante em questão
        NotificationAudience specificStudent = NotificationAudience.SPECIFIC_STUDENT;

        String title = "Alerta de Aproximação do Ônibus";
        String message = "O ônibus está a " + Math.round(distance) + " metros de você. Fique atento";
        String link = "/travels/" + travelId + "/proximity/student/" + studentId;

        Map<String, String> data = Map.of(
                "eventType", "STUDENT_PROXIMITY",
                "travelId", travelId.toString(),
                "studentId", studentId.toString(),
                "distance", distance.toString(),
                "zone", zone,
                "alertType", alertType
        );

        PushNotificationCommandDTO studentCommand =
                new PushNotificationCommandDTO(specificStudent, null, travelId, studentId, null,null, title, message, link, Priority.NORMAL, data);

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

            long multiplyNotificationTime = INVALID_ROUTE_LAST_NOTIFY_TIME * countInvalidRouteNotifications;

            /*
            * múltiplica o tempo de envio de cada notificação com base na quantidade já enviada
            * 1 = assim que entra na viagem | 2 = 5min | 3 = 15min | 4 = 20min
            * envia no máximo 4 notificações por padrão (0-3)
            * */
            if (totalTimeOfLastNotify >= multiplyNotificationTime && countInvalidRouteNotifications < MAX_SENT_NOT_ASSOCIATED_ROUTE_STOP_NOTIFICATIONS) {
                log.info("[InvalidRoute - notify] - Notificação enviada de número {} enviada: ", countInvalidRouteNotifications);

                firebaseNotificationSender.sendPushNotification(studentCommand);

                Instant notifyAt = Instant.now();
                countInvalidRouteNotifications += 1;

                redisNotificationService.putInvalidRouteLastNotifyData(travelId, studentId, notifyAt, countInvalidRouteNotifications);
            }

        }
    }

}
