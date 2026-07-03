package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Service;

import java.util.Map;

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
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem
        NotificationAudience adminNotification = NotificationAudience.ADMIN_ONLY;

        String title = "Nova viagem criada";
        String message = "Uma nova viagem para o turno" + travel.getTravelPeriod() + "foi criada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CREATED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // students
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(travelStudents, null, null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(driverNotification, travel.getDriver().getId(), null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // admins
        PushNotificationCommandDTO admin = new PushNotificationCommandDTO(adminNotification, null, travel.getDriver().getCustomer().getId(), travel.getId(), title, message, link, Priority.NORMAL, data);

        // envia as notificações ao firebase
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(admin);
    }

    // INICÍO DE VIAGEM
    public void sendTravelStartedNotification(Travel travel) {
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem
        NotificationAudience adminNotification = NotificationAudience.ADMIN_ONLY;

        String title = "Viagem iniciada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi iniciada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_STARTED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // students
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(travelStudents, null, null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(driverNotification, travel.getDriver().getId(), null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // admins
        PushNotificationCommandDTO admin = new PushNotificationCommandDTO(adminNotification, null, travel.getDriver().getCustomer().getId(), travel.getId(), title, message, link, Priority.NORMAL, data);

        // envia as notificações ao firebase
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(admin);
    }

    // FIM DE VIAGEM
    public void sendTravelEndedNotification(Travel travel) {
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem
        NotificationAudience adminNotification = NotificationAudience.ADMIN_ONLY;

        String title = "Viagem finalizada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi finalizada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_ENDED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // students
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(travelStudents, null, null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(driverNotification, travel.getDriver().getId(), null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // admins
        PushNotificationCommandDTO admin = new PushNotificationCommandDTO(adminNotification, null, travel.getDriver().getCustomer().getId(), travel.getId(), title, message, link, Priority.NORMAL, data);

        // envia as notificações ao firebase
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(admin);
    }

    // CANCELAMENTO DE VIAGEM
    public void sendTravelCanceledNotification(Travel travel) {
        NotificationAudience travelStudents = NotificationAudience.TRAVEL_STUDENTS;
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_USER; // envia apenas para o motorista da viagem
        NotificationAudience adminNotification = NotificationAudience.ADMIN_ONLY;

        String title = "Viagem cancelada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi cancelada";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CANCELED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // students
        PushNotificationCommandDTO students = new PushNotificationCommandDTO(travelStudents, null, null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // driver
        PushNotificationCommandDTO driver = new PushNotificationCommandDTO(driverNotification, travel.getDriver().getId(), null, travel.getId(), title, message, link, Priority.NORMAL, data);

        // admins
        PushNotificationCommandDTO admin = new PushNotificationCommandDTO(adminNotification, null, travel.getDriver().getCustomer().getId(), travel.getId(), title, message, link, Priority.NORMAL, data);

        // envia as notificações ao firebase
        firebaseNotificationSender.sendPushNotification(students);
        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(admin);
    }
}
