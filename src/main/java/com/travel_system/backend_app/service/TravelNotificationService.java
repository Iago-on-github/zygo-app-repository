package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.checkerframework.checker.units.qual.N;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/*
* notificações estáticas da viagem rodando async
* */
@Service
@Async("staticNotificationTaskExecutor")
public class TravelNotificationService {

    private final FirebaseNotificationSender firebaseNotificationSender;

    private final TravelRepository travelRepository;

    public TravelNotificationService(FirebaseNotificationSender firebaseNotificationSender, TravelRepository travelRepository) {
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.travelRepository = travelRepository;
    }

    /*
    * criação de nova viagem:
    * - notifica apenas os alunos que são cadastrados no período da viagem
    * - notifica todos dos admins
    * - notifica os responsáveis pelos alunos
    * */
    public void sendTravelCreatedNotification(Travel travel) {
        NotificationAudience adminNotification = NotificationAudience.CUSTOMER_ADMINS;
        NotificationAudience responsibleNotification = NotificationAudience.TRAVEL_RESPONSIBLES;
        NotificationAudience periodStudents = NotificationAudience.PERIOD_STUDENTS;

        String title = "Nova viagem criada";
        String message = "Uma nova viagem para o turno" + travel.getTravelPeriod() + " foi criada às " + travel.getCreatedAt();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CREATED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // converte para usar na busca por período
        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // admin
        PushNotificationCommandDTO adminCommandDTO = new PushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        // responsible
        PushNotificationCommandDTO responsibleCommandDTO = new PushNotificationCommandDTO(
                responsibleNotification, null, travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        // estudante
        PushNotificationCommandDTO periodStudentsCommandDTO = new PushNotificationCommandDTO(
                periodStudents, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(adminCommandDTO);
        firebaseNotificationSender.sendPushNotification(responsibleCommandDTO);
        firebaseNotificationSender.sendPushNotification(periodStudentsCommandDTO);
    }

    /*
     * criação de nova viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelStartedNotification(Travel travel) {
        // aqui usa-se period students pq alguns alunos podem não ter se conectado ainda à viagem, por isso, é importante notifica-los do início

        NotificationAudience travelResponsiblesNotification = NotificationAudience.TRAVEL_RESPONSIBLES;
        NotificationAudience adminNotification = NotificationAudience.CUSTOMER_ADMINS;
        NotificationAudience periodStudentsNotification = NotificationAudience.PERIOD_STUDENTS;

        String title = "Viagem iniciada";
        String message = "A viagem do turno " + travel.getTravelPeriod() + " foi iniciada às " + travel.getStartHourTravel();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_STARTED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // responsáveis
        PushNotificationCommandDTO responsiblesCommandDTO =
                new PushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        PushNotificationCommandDTO adminCommandDTO =
                new PushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        PushNotificationCommandDTO studentsCommandDTO =
                new PushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendPushNotification(adminCommandDTO);
        firebaseNotificationSender.sendPushNotification(studentsCommandDTO);
    }

    /*
     * finalzação de viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelEndedNotification(Travel travel) {
        NotificationAudience travelResponsiblesNotification = NotificationAudience.TRAVEL_RESPONSIBLES;
        NotificationAudience adminNotification = NotificationAudience.CUSTOMER_ADMINS;
        NotificationAudience periodStudentsNotification = NotificationAudience.PERIOD_STUDENTS;

        String title = "Viagem finalizada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi finalizada às " + travel.getEndHourTravel();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_ENDED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // responsáveis
        PushNotificationCommandDTO responsiblesCommandDTO =
                new PushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        PushNotificationCommandDTO adminCommandDTO =
                new PushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        PushNotificationCommandDTO studentsCommandDTO =
                new PushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendPushNotification(adminCommandDTO);
        firebaseNotificationSender.sendPushNotification(studentsCommandDTO);
    }

    /*
     * cancelamento de viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelCanceledNotification(Travel travel) {
        NotificationAudience travelResponsiblesNotification = NotificationAudience.TRAVEL_RESPONSIBLES;
        NotificationAudience adminNotification = NotificationAudience.CUSTOMER_ADMINS;
        NotificationAudience periodStudentsNotification = NotificationAudience.PERIOD_STUDENTS;

        String title = "Viagem cancelada";
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi cancelada. Verifique os canais de comunicação para mais informações";
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_CANCELED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // responsáveis
        PushNotificationCommandDTO responsiblesCommandDTO =
                new PushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        PushNotificationCommandDTO adminCommandDTO =
                new PushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        PushNotificationCommandDTO studentsCommandDTO =
                new PushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendPushNotification(adminCommandDTO);
        firebaseNotificationSender.sendPushNotification(studentsCommandDTO);
    }

    /*
     * mudança de motodista na viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * - notifica o novo motorista em específico
     * */
    public void sendDriverChangedNotification(Travel travel, Driver newDriver) {
        NotificationAudience driverNotification = NotificationAudience.SPECIFIC_DRIVER; // envia apenas para o novo motorista da viagem
        NotificationAudience travelResponsiblesNotification = NotificationAudience.TRAVEL_RESPONSIBLES;
        NotificationAudience adminNotification = NotificationAudience.CUSTOMER_ADMINS;
        NotificationAudience periodStudentsNotification = NotificationAudience.PERIOD_STUDENTS;

        String title = "Mudança de motorista";
        String message = "Houve uma modificação no motorista da viagem do turno " + travel.getTravelPeriod() + ". Novo motorista: " + newDriver.getName();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "DRIVER_CHANGED",
                "travelId", travel.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // driver
        PushNotificationCommandDTO driver =
                new PushNotificationCommandDTO(driverNotification, null, travel.getId(), null, newDriver.getId(),null, title, message, link, Priority.NORMAL, data);

        // responsáveis
        PushNotificationCommandDTO responsibles =
                new PushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admins
        PushNotificationCommandDTO admins =
                new PushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        PushNotificationCommandDTO students =
                new PushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(driver);
        firebaseNotificationSender.sendPushNotification(responsibles);
        firebaseNotificationSender.sendPushNotification(admins);
        firebaseNotificationSender.sendPushNotification(students);
    }

    /*
    * embarque de estudantes:
    * - notifica o responsável pelo estudante embarcado
    * */
    public void sendEmbarkStudentNotificationToResponsible(Travel travel, Student student) {
        NotificationAudience studentResponsible = NotificationAudience.STUDENT_RESPONSIBLE;

        String title = "Estudante entrou na viagem";
        String message = student.getName() + " acabou de entrar na viagem do turno " + travel.getTravelPeriod();
        String link = "/travels/" + travel.getId() + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_STUDENT_EMBARKED",
                "travelId", travel.getId().toString(),
                "studentId", student.getId().toString(),
                "period", travel.getTravelPeriod().toString()
        );

        // responsible
        PushNotificationCommandDTO responsible =
                new PushNotificationCommandDTO(studentResponsible, null, travel.getId(), student.getId(), null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(responsible);
    }

    /*
     * embarque de estudantes:
     * - notifica o responsável pelo estudante embarcado
     * */
    public void sendDisembarkStudentNotificationToResponsible(UUID travelId, String studentName, UUID studentId, Instant disembarkHour) {
        NotificationAudience studentResponsible = NotificationAudience.STUDENT_RESPONSIBLE;

        String title = "Estudante saiu da viagem";
        String message = studentName + " acabou de sair da viagem exatamente às " + disembarkHour;
        String link = "/travels/" + travelId + "/tracking";

        Map<String, String> data = Map.of(
                "eventType", "TRAVEL_STUDENT_DISEMBARKED",
                "studentId", studentId.toString(),
                "travelId", travelId.toString(),
                "disembarkHour", disembarkHour.toString()
        );

        // responsible
        PushNotificationCommandDTO responsible =
                new PushNotificationCommandDTO(studentResponsible, null, travelId, studentId, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendPushNotification(responsible);
    }

    // timeable change

}
