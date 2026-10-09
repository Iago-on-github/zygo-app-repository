package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.GlobalAppConstants;
import com.travel_system.backend_app.config.constants.NotificationConstants;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.TravelPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.TravelNotificationAudience;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.DateTimeFormats;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/*
* notificações estáticas da viagem rodando async
* */
@Service
@Async("staticNotificationTaskExecutor")
public class TravelNotificationService {
    private final Logger log = LoggerFactory.getLogger(TravelNotificationService.class);

    private final RedisTemplate<String, String> redisTemplate;

    private final FirebaseNotificationSender firebaseNotificationSender;
    private final DateTimeFormats dateTimeFormats;
    private final RedisNotificationService redisNotificationService;

    private final TravelRepository travelRepository;

    public TravelNotificationService(RedisTemplate<String, String> redisTemplate, FirebaseNotificationSender firebaseNotificationSender, DateTimeFormats dateTimeFormats, RedisNotificationService redisNotificationService, TravelRepository travelRepository) {
        this.redisTemplate = redisTemplate;
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.dateTimeFormats = dateTimeFormats;
        this.redisNotificationService = redisNotificationService;
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
     * inicio de  viagem:
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
        String message = "A viagem do turno " + travel.getTravelPeriod() + " foi iniciada às " + travel.getStartHourTravelAt();
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
        String message = "A viagem do turno" + travel.getTravelPeriod() + "foi finalizada às " + travel.getEndHourTravelAt();
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
     * desembarque de estudantes:
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

    /*
    * agendamento de viagem
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     *  */
    public void sendTravelScheduleNotification(Travel travel) {
        TravelNotificationAudience periodStudents = TravelNotificationAudience.PERIOD_STUDENTS;
        TravelNotificationAudience customerAdmins = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience studentResponsible = TravelNotificationAudience.STUDENT_RESPONSIBLE;

        String title = "Nova viagem agendada";
        String message = "Uma nova viagem foi agendada para o período " + travel.getTravelPeriod() + ", às " + dateTimeFormats.formatInstantDate(travel.getScheduledStartAt());
        String link = "/travels/" + travel.getId() + "/scheduled";

        Map<String, String> data = Map.of(
                "eventType", "SCHEDULED_TRAVEL",
                "travelId", travel.getId().toString(),
                "startHour", travel.getScheduledStartAt().toString(),
                "createdBy", travel.getTravelScheduleCreatedBy(),
                "period", travel.getTravelPeriod().toString()
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        // students
        TravelPushNotificationCommandDTO students =
                new TravelPushNotificationCommandDTO(periodStudents, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        // admin
        TravelPushNotificationCommandDTO admins =
                new TravelPushNotificationCommandDTO(customerAdmins, travel.getCustomerId(), travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        // responsible
        TravelPushNotificationCommandDTO responsible =
                new TravelPushNotificationCommandDTO(studentResponsible, null, travel.getId(), null, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendTravelNotification(students);
        firebaseNotificationSender.sendTravelNotification(admins);
        firebaseNotificationSender.sendTravelNotification(responsible);
    }

    /*
     * lembretes de início de viagem (ex. [inicio 10h] -> 09h -> 09h40 -> 9h55)
     * - notifica apenas os alunos que são cadastrados no período da viagem
     * - notifica todos dos admins
     * - notifica os responsáveis pelos alunos
     * - notifica o motorista responsável pela viagem
    * */
    public void sendScheduleRemindersTravelStart(Travel travel) {

        Optional<Long> allowToNotify = isAllowToNotify(
                travel.getId(),
                travel.getScheduledStartAt()
        );

        if (allowToNotify.isEmpty()) {
            return;
        }

        long minutes = allowToNotify.get();

        TravelNotificationAudience periodStudents = TravelNotificationAudience.PERIOD_STUDENTS;
        TravelNotificationAudience customerAdmins = TravelNotificationAudience.CUSTOMER_ADMINS;
        TravelNotificationAudience studentResponsible = TravelNotificationAudience.STUDENT_RESPONSIBLE;
        TravelNotificationAudience driverNotification = TravelNotificationAudience.SPECIFIC_DRIVER;

        String title = "Lembrete de início de viagem";
        String message = "A viagem agendada pelo motorista "
                + travel.getTravelScheduleCreatedBy()
                + " começa às "
                + dateTimeFormats.formatInstantDate(travel.getScheduledStartAt());

        String link = "/travels/" + travel.getId() + "/reminder";

        Map<String, String> data = Map.of(
                "eventType", "REMINDER_START_SCHEDULED_TRAVEL",
                "travelId", travel.getId().toString(),
                "startHour", travel.getScheduledStartAt().toString(),
                "createdBy", travel.getTravelScheduleCreatedBy(),
                "period", travel.getTravelPeriod().toString(),
                "minutes", Long.toString(minutes)
        );

        Shift shift = Shift.valueOf(travel.getTravelPeriod().name());

        TravelPushNotificationCommandDTO students =
                new TravelPushNotificationCommandDTO(periodStudents, travel.getCustomerId(), travel.getId(), null, null, shift, title, message, link, Priority.NORMAL, data);

        TravelPushNotificationCommandDTO admins =
                new TravelPushNotificationCommandDTO(customerAdmins, travel.getCustomerId(), travel.getId(), null, null, null, title, message, link, Priority.HIGH, data);

        TravelPushNotificationCommandDTO responsible =
                new TravelPushNotificationCommandDTO(studentResponsible, null, travel.getId(), null, null, null, title, message, link, Priority.HIGH, data);

        TravelPushNotificationCommandDTO driver =
                new TravelPushNotificationCommandDTO(driverNotification, null, travel.getId(), null, travel.getDriver().getId(), null, title, message, link, Priority.HIGH, data);

        /*
         * calcula o ttl p/ manter a chave no Redis por, no mínimo, 24 horas após o início previsto da viagem,
         * evitando a expiração prematura do controle dos lembretes.
         * */
        Duration ttl = Duration.between(Instant.now(), travel.getScheduledStartAt()).plus(Duration.ofDays(1));

        if (ttl.compareTo(Duration.ofDays(1)) < 0) {
            ttl = Duration.ofDays(1);
        }

        try {
            firebaseNotificationSender.sendTravelNotification(students);
            firebaseNotificationSender.sendTravelNotification(admins);
            firebaseNotificationSender.sendTravelNotification(responsible);
            firebaseNotificationSender.sendTravelNotification(driver);

            redisNotificationService.markReminderStartTravelNotificationAsSent(travel.getId(), minutes, ttl);

        } catch (Exception e) {
            log.error("[sendScheduleRemindersTravelStart] Erro ao enviar lembretes da viagem {}", travel.getId(), e);

            redisNotificationService.releaseReminderStartTravelNotification(travel.getId(), minutes);
        }
    }

    // verifica individualmente os lembretes e reserva no Redis o primeiro que estiver elegível
    private Optional<Long> isAllowToNotify(UUID travelId, Instant scheduledStartAt) {
        Instant now = Instant.now();

        List<Long> reminderIntervals = List.of(
                NotificationConstants.NOTIFICATION_SCHEDULE_SIXTY_MINUTES,
                NotificationConstants.NOTIFICATION_SCHEDULE_TWENTY_MINUTES,
                NotificationConstants.NOTIFICATION_SCHEDULE_FIVE_MINUTES
        );

        /*
         * calcula o ttl p/ manter a chave no Redis por, no mínimo, 24 horas após o início previsto da viagem,
         * evitando a expiração prematura do controle dos lembretes.
         * */
        Duration ttl = Duration.between(now, scheduledStartAt).plus(Duration.ofDays(1));

        if (ttl.compareTo(Duration.ofDays(1)) < 0) {
            ttl = Duration.ofDays(1);
        }

        for (Long minutes : reminderIntervals) {
            if (!calculateExactlyTime(now, scheduledStartAt, minutes)) {
                continue;
            }

            boolean reserved = redisNotificationService.reserveReminderStartTravelNotification(travelId, minutes, ttl);

            if (reserved) {
                return Optional.of(minutes);
            }
        }

        return Optional.empty();
    }

    // verifica se o horário do lembrete foi atingido, respeitando uma tolerância de 1 minuto
    private boolean calculateExactlyTime(Instant now, Instant scheduledStartAt, long minutes) {
        Duration remaining = Duration.between(now, scheduledStartAt);
        Duration reminderTime = Duration.ofMinutes(minutes);

        Duration elapsedSinceReminder = reminderTime.minus(remaining);

        return !elapsedSinceReminder.isNegative() && elapsedSinceReminder.compareTo(Duration.ofMinutes(1)) <= 0;
    }
}
