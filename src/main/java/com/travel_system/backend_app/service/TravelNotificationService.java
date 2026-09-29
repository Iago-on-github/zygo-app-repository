package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.TravelPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.TravelNotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
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
        TravelNotificationAudience adminNotification = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience responsibleNotification = TravelNotificationAudience.TRAVEL_RESPONSIBLES;
        TravelNotificationAudience periodStudents = TravelNotificationAudience.PERIOD_STUDENTS;

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
        TravelPushNotificationCommandDTO adminCommandDTO = new TravelPushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        // responsible
        TravelPushNotificationCommandDTO responsibleCommandDTO = new TravelPushNotificationCommandDTO(
                responsibleNotification, null, travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        // estudante
        TravelPushNotificationCommandDTO periodStudentsCommandDTO = new TravelPushNotificationCommandDTO(
                periodStudents, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(adminCommandDTO);
        firebaseNotificationSender.sendTravelNotification(responsibleCommandDTO);
        firebaseNotificationSender.sendTravelNotification(periodStudentsCommandDTO);
    }

    /*
     * criação de nova viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelStartedNotification(Travel travel) {
        // aqui usa-se period students pq alguns alunos podem não ter se conectado ainda à viagem, por isso, é importante notifica-los do início

        TravelNotificationAudience travelResponsiblesNotification = TravelNotificationAudience.TRAVEL_RESPONSIBLES;
        TravelNotificationAudience adminNotification = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience periodStudentsNotification = TravelNotificationAudience.PERIOD_STUDENTS;

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
        TravelPushNotificationCommandDTO responsiblesCommandDTO =
                new TravelPushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        TravelPushNotificationCommandDTO adminCommandDTO =
                new TravelPushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        TravelPushNotificationCommandDTO studentsCommandDTO =
                new TravelPushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendTravelNotification(adminCommandDTO);
        firebaseNotificationSender.sendTravelNotification(studentsCommandDTO);
    }

    /*
     * finalzação de viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelEndedNotification(Travel travel) {
        TravelNotificationAudience travelResponsiblesNotification = TravelNotificationAudience.TRAVEL_RESPONSIBLES;
        TravelNotificationAudience adminNotification = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience periodStudentsNotification = TravelNotificationAudience.PERIOD_STUDENTS;

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
        TravelPushNotificationCommandDTO responsiblesCommandDTO =
                new TravelPushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        TravelPushNotificationCommandDTO adminCommandDTO =
                new TravelPushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        TravelPushNotificationCommandDTO studentsCommandDTO =
                new TravelPushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendTravelNotification(adminCommandDTO);
        firebaseNotificationSender.sendTravelNotification(studentsCommandDTO);
    }

    /*
     * cancelamento de viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * */
    public void sendTravelCanceledNotification(Travel travel) {
        TravelNotificationAudience travelResponsiblesNotification = TravelNotificationAudience.TRAVEL_RESPONSIBLES;
        TravelNotificationAudience adminNotification = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience periodStudentsNotification = TravelNotificationAudience.PERIOD_STUDENTS;

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
        TravelPushNotificationCommandDTO responsiblesCommandDTO =
                new TravelPushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        TravelPushNotificationCommandDTO adminCommandDTO =
                new TravelPushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        TravelPushNotificationCommandDTO studentsCommandDTO =
                new TravelPushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(responsiblesCommandDTO);
        firebaseNotificationSender.sendTravelNotification(adminCommandDTO);
        firebaseNotificationSender.sendTravelNotification(studentsCommandDTO);
    }

    /*
     * mudança de motodista na viagem:
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * - notifica o novo motorista em específico
     * */
    public void sendDriverChangedNotification(Travel travel, Driver newDriver) {
        TravelNotificationAudience driverNotification = TravelNotificationAudience.SPECIFIC_DRIVER; // envia apenas para o novo motorista da viagem
        TravelNotificationAudience travelResponsiblesNotification = TravelNotificationAudience.TRAVEL_RESPONSIBLES;
        TravelNotificationAudience adminNotification = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience periodStudentsNotification = TravelNotificationAudience.PERIOD_STUDENTS;

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
        TravelPushNotificationCommandDTO driver =
                new TravelPushNotificationCommandDTO(driverNotification, null, travel.getId(), null, newDriver.getId(),null, title, message, link, Priority.NORMAL, data);

        // responsáveis
        TravelPushNotificationCommandDTO responsibles =
                new TravelPushNotificationCommandDTO(travelResponsiblesNotification, null, travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admins
        TravelPushNotificationCommandDTO admins =
                new TravelPushNotificationCommandDTO(adminNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // students period
        TravelPushNotificationCommandDTO students =
                new TravelPushNotificationCommandDTO(periodStudentsNotification, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(driver);
        firebaseNotificationSender.sendTravelNotification(responsibles);
        firebaseNotificationSender.sendTravelNotification(admins);
        firebaseNotificationSender.sendTravelNotification(students);
    }

    /*
    * embarque de estudantes:
    * - notifica o responsável pelo estudante embarcado
    * */
    public void sendEmbarkStudentNotificationToResponsible(Travel travel, Student student) {
        TravelNotificationAudience studentResponsible = TravelNotificationAudience.STUDENT_RESPONSIBLE;

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
        TravelPushNotificationCommandDTO responsible =
                new TravelPushNotificationCommandDTO(studentResponsible, null, travel.getId(), student.getId(), null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(responsible);
    }

    /*
     * embarque de estudantes:
     * - notifica o responsável pelo estudante embarcado
     * */
    public void sendDisembarkStudentNotificationToResponsible(UUID travelId, String studentName, UUID studentId, Instant disembarkHour) {
        TravelNotificationAudience studentResponsible = TravelNotificationAudience.STUDENT_RESPONSIBLE;

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
        TravelPushNotificationCommandDTO responsible =
                new TravelPushNotificationCommandDTO(studentResponsible, null, travelId, studentId, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(responsible);
    }

    // timeable change

}
