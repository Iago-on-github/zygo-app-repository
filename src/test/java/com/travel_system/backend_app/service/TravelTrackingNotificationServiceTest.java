package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TravelTrackingNotificationServiceTest {

    @InjectMocks
    private TravelTrackingNotificationService trackingNotificationService;

    @Mock
    private FirebaseNotificationSender firebaseNotificationSender;

    @Nested
    @DisplayName("Cenários sobre as notificações atreladas ao movement [slow/stopped]")
    class movementNotification {
        Travel travel;
        Driver driver;
        VelocityAnalysisDTO velocityAnalysis;
        StudentProximityNotificationMessage studentProximityNotificationMessage;
        @BeforeEach
        void setUp() {
            Customer customer = new Customer(UUID.randomUUID(), "Prefeitura de Coração de Maria", "prefeitura-coração-de-maria", "000000000000", true, new City(), ClientSector.PUBLIC_CLIENT, null, Instant.now(), null);

            driver = new Driver(UUID.randomUUID(), "joao.silva@email.com", "senha123", "João", "Silva", "+55 11 99999-9999", "https://example.com/profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, "São Paulo - Capital", 1250);

            travel = new Travel(UUID.randomUUID(), TravelStatus.PENDING, driver, Instant.now(), Instant.now().plusSeconds(5000), TravelPeriod.AFTERNOON, null, "polylineRouteExemple", 3032.3, 40.0, -12.973456, -38.501234, -12.985678, -38.512345, "Feira de Santana", customer);

            velocityAnalysis = new VelocityAnalysisDTO(75.3, 231L, null, null, MovementState.SLOW);

            studentProximityNotificationMessage = new StudentProximityNotificationMessage(UUID.randomUUID(), UUID.randomUUID(), 50.0, "NEAR", Instant.now().toString(), "teste");
        }

        @Test
        @DisplayName("Deve disparar duas notificações informando que o ônibus está se movimentando com lentidão com os dados corretos ")
        void shouldSendTrackingSlowMovementNotification() {
            String expectedTitle = "Alerta de ônibus lento";
            String expectedMessage = "O ônibus está se movimentando lentamente. Fique atento.";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "VEHICLE_SLOW",
                    "travelId", travel.getId().toString(),
                    "movementState", velocityAnalysis.movementState().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> captorCommand = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            trackingNotificationService.sendTrackingSlowMovementNotification(travel, velocityAnalysis);

            verify(firebaseNotificationSender, times(2)).sendPushNotification(captorCommand.capture());

            List<PushNotificationCommandDTO> commandAllValues = captorCommand.getAllValues();

            PushNotificationCommandDTO students = commandAllValues.getFirst();
            assertEquals(NotificationAudience.EMBARKED_TRAVEL_STUDENTS, students.notificationAudience());
            assertEquals(expectedTitle, students.title());
            assertEquals(expectedMessage, students.message());
            assertEquals(expectedLink, students.link());
            assertEquals(expectedData, students.data());
            assertEquals(Priority.HIGH, students.priority());

            PushNotificationCommandDTO driver = commandAllValues.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driver.notificationAudience());
            assertEquals(expectedTitle, driver.title());
            assertEquals(expectedMessage, driver.message());
            assertEquals(expectedLink, driver.link());
            assertEquals(expectedData, driver.data());
            assertEquals(Priority.HIGH, driver.priority());
        }

        @Test
        @DisplayName("Deve disparar duas notificações informando que o ônibus está parado com os dados corretos")
        void shouldSendTrackingStoppedMovementNotification() {
            String expectedTitle = "Alerta de ônibus parado";
            String expectedMessage = "O ônibus está parado, possíveis problemas na via. Fique atento.";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "VEHICLE_STOPPED",
                    "travelId", travel.getId().toString(),
                    "movementState", velocityAnalysis.movementState().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> captorCommand = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            trackingNotificationService.sendTrackingStoppedMovementNotification(travel, velocityAnalysis);

            verify(firebaseNotificationSender, times(2)).sendPushNotification(captorCommand.capture());

            List<PushNotificationCommandDTO> commandAllValues = captorCommand.getAllValues();

            PushNotificationCommandDTO students = commandAllValues.getFirst();
            assertEquals(NotificationAudience.EMBARKED_TRAVEL_STUDENTS, students.notificationAudience());
            assertEquals(expectedTitle, students.title());
            assertEquals(expectedMessage, students.message());
            assertEquals(expectedLink, students.link());
            assertEquals(expectedData, students.data());
            assertEquals(Priority.HIGH, students.priority());

            PushNotificationCommandDTO driver = commandAllValues.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driver.notificationAudience());
            assertEquals(expectedTitle, driver.title());
            assertEquals(expectedMessage, driver.message());
            assertEquals(expectedLink, driver.link());
            assertEquals(expectedData, driver.data());
            assertEquals(Priority.HIGH, driver.priority());
        }

        @Test
        @DisplayName("Deve disparar notificação de desembarque automático com sucesso")
        void shouldSendAutoDisconnectStudentNotification() {
            UUID studentId = UUID.randomUUID();

            String expectedTitle = "Desconexão automática";
            String expectedMessage = "Você foi desembarcado automaticamente da viagem por estar distante do ônibus";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "AUTO_DISCONNECTED_STUDENT",
                    "travelId", travel.getId().toString(),
                    "studentStatus", "AUTO_DISCONNECTED"
            );

            ArgumentCaptor<PushNotificationCommandDTO> captorCommand = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            trackingNotificationService.sendAutoDisconnectStudentNotification(travel, studentId);

            verify(firebaseNotificationSender, times(1)).sendPushNotification(captorCommand.capture());

            PushNotificationCommandDTO students = captorCommand.getValue();
            assertEquals(NotificationAudience.SPECIFIC_USER, students.notificationAudience());
            assertEquals(expectedTitle, students.title());
            assertEquals(expectedMessage, students.message());
            assertEquals(expectedLink, students.link());
            assertEquals(expectedData, students.data());
            assertEquals(Priority.NORMAL, students.priority());
        }

        @Test
        @DisplayName("Deve disparar notificações pushs de proximidade do veículo com sucesso")
        void sendCheckProximityAlertsNotification() {
            UUID travelId = studentProximityNotificationMessage.travelId();

            String expectedTitle = "Alerta de ônibus se aproximando";
            String expectedMessage = "O ônibus está a " + Math.round(studentProximityNotificationMessage.distance()) + " metros de você.";
            String expectedLink = "/travels/" + travelId + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "STUDENT_PROXIMITY",
                    "travelId", travelId.toString(),
                    "distance", studentProximityNotificationMessage.distance().toString(),
                    "zone", studentProximityNotificationMessage.zone(),
                    "alertType", studentProximityNotificationMessage.alertType(),
                    "timestamp", studentProximityNotificationMessage.timestamp()
            );

            ArgumentCaptor<PushNotificationCommandDTO> captorCommand = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            trackingNotificationService.sendCheckProximityAlertsNotification(studentProximityNotificationMessage);

            verify(firebaseNotificationSender, times(1)).sendPushNotification(captorCommand.capture());

            PushNotificationCommandDTO students = captorCommand.getValue();
            assertEquals(NotificationAudience.SPECIFIC_USER, students.notificationAudience());
            assertEquals(expectedTitle, students.title());
            assertEquals(expectedMessage, students.message());
            assertEquals(expectedLink, students.link());
            assertEquals(expectedData, students.data());
            assertEquals(Priority.NORMAL, students.priority());

        }
    }
}