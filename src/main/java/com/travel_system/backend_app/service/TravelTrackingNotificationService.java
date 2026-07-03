package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/*
* notificações tracking da viagem
* */

@Service
public class TravelTrackingNotificationService {
    private final FirebaseNotificationSender firebaseNotificationSender;

    public TravelTrackingNotificationService(FirebaseNotificationSender firebaseNotificationSender) {
        this.firebaseNotificationSender = firebaseNotificationSender;
    }

    // SLOW
    public void sendTrackingSlowMovementNotification(Travel travel, VelocityAnalysisDTO velocityAnalysis) {
        UUID travelId = travel.getId();
        UUID driverId = travel.getDriver().getId();
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
        handleMovementNotification(travelId, driverId, title, message, link, data);
    }

    // STOPPED
    public void sendTrackingStoppedMovementNotification(Travel travel, VelocityAnalysisDTO velocityAnalysis) {
        UUID travelId = travel.getId();
        UUID driverId = travel.getDriver().getId();
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
        handleMovementNotification(travelId, driverId, title, message, link, data);
    }

    // handle movement notification
    private void handleMovementNotification(UUID travelId, UUID driverId, String title, String message, String link, Map<String, String> data) {
        NotificationAudience travelStudents = NotificationAudience.EMBARKED_TRAVEL_STUDENTS; // envia para os estudantes embarcados na viagem
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem

        // dto notificação estudantes
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(
                travelStudents, null, null, travelId, title, message, link, Priority.HIGH, data);

        // dto notificação driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(
                driverNotification, driverId, null, travelId, title, message, link, Priority.HIGH, data);

        // envia as notificações
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
    }
}
