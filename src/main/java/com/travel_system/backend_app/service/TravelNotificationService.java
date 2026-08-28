package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/*
* notificações estáticas da viagem
* */
@Service
public class TravelNotificationService {

    private final FirebaseNotificationSender firebaseNotificationSender;

    private final TravelRepository travelRepository;

    public TravelNotificationService(FirebaseNotificationSender firebaseNotificationSender, TravelRepository travelRepository) {
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.travelRepository = travelRepository;
    }

    // CREATE TRAVEL
    public void sendTravelCreatedNotification(Travel travel) {
        String title = "Nova viagem criada";
        String message = "Uma nova viagem para o turno" + travel.getTravelPeriod() + "foi criada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CREATED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // metodo handler auxiliar
        handleTravelNotification(travel, title, message, link, data);
    }

    // INICÍO DE VIAGEM
    public void sendTravelStartedNotification(Travel travel) {
        String title = "Viagem iniciada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi iniciada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_STARTED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // metodo handler auxiliar
        handleTravelNotification(travel, title, message, link, data);
    }

    // FIM DE VIAGEM
    public void sendTravelEndedNotification(Travel travel) {
        String title = "Viagem finalizada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi finalizada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_ENDED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // metodo handler auxiliar
        handleTravelNotification(travel, title, message, link, data);
    }

    // CANCELAMENTO DE VIAGEM
    public void sendTravelCanceledNotification(Travel travel) {
        String title = "Viagem cancelada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi cancelada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CANCELED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // metodo handler auxiliar
        handleTravelNotification(travel, title, message, link, data);
    }

    // MUDANÇA DE MOTORISTA
    public void sendDriverChangedNotification(Travel travel, Driver newDriver) {
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o novo motorista da viagem

        String title = "Mudança de motorista";
        String message = "Houve uma modificação no motorista da viagem do turno" + travel.getTravelPeriod() + ". Novo motorista: " + newDriver.getName();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "DRIVER_CHANGED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // students
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(travelStudents, null, null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(driverNotification, newDriver.getId(), null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // envia as notificações ao firebase
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
    }

    // timeable change

    // HANDLER
    private void handleTravelNotification(Travel travel, String title, String message, String link, Map<String, String> data) {
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem
        NotificationAudience adminNotification = NotificationAudience.ADMIN_ONLY;

        UUID travelId = travel.getId();
        UUID driverId = travel.getDriver().getId();
        UUID customerId = travel.getCustomerId();

        // notificação estudantes
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(
                travelStudents, null, null, travelId, title, message, link, Priority.NORMAL, data
        );

        // Notificação motorista
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(
                driverNotification, driverId, null, travelId, title, message, link, Priority.NORMAL, data
        );

        // Notificação administradores
        PushNotificationCommandDTO admin = new PushNotificationCommandDTO(
                adminNotification, null, customerId, travelId, title, message, link, Priority.NORMAL, data
        );

        // envia todas as notificações
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(admin);
    }
}
