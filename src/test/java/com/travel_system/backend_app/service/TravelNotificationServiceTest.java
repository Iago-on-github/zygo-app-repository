package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TravelNotificationServiceTest {

    @InjectMocks
    private TravelNotificationService travelNotificationService;

    @Mock
    private FirebaseNotificationSender firebaseNotificationSender;

    @Nested
    class sendTravelCreatedNotification {
        Travel travel;
        Driver driver;

        @BeforeEach
        void setUp() {
            Customer customer = new Customer(UUID.randomUUID(), "Prefeitura de Coração de Maria", "prefeitura-coração-de-maria", "000000000000", true, new City(), ClientSector.PUBLIC_CLIENT, null, Instant.now(), null);

            driver = new Driver(UUID.randomUUID(), "joao.silva@email.com", "senha123", "João", "Silva", "+55 11 99999-9999", "https://example.com/profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, "São Paulo - Capital", 1250);

            travel = new Travel(UUID.randomUUID(), TravelStatus.PENDING, driver, Instant.now(), Instant.now().plusSeconds(5000), TravelPeriod.AFTERNOON, null, "polylineRouteExemple", 3032.3, 40.0, -12.973456, -38.501234, -12.985678, -38.512345, "Feira de Santana", customer);
        }

        @Test
        @DisplayName("Deve disparar as três notificações do ciclo de criação com os dados corretos")
        void shouldSendNotificationToCreateTravel() {
            String expectedTitle = "Nova viagem criada";
            String expectedMessage = "Uma nova viagem para o turno" + travel.getTravelPeriod() + "foi criada";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "TRAVEL_CREATED",
                    "travelId", travel.getId().toString(),
                    "period", travel.getTravelPeriod().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> commandCaptor = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            travelNotificationService.sendTravelCreatedNotification(travel);

            verify(firebaseNotificationSender, times(3)).sendPushNotification(commandCaptor.capture());

            List<PushNotificationCommandDTO> capturedCommands = commandCaptor.getAllValues();

            PushNotificationCommandDTO studentsCommand = capturedCommands.getFirst();
            assertEquals(NotificationAudience.TRAVEL_STUDENTS, studentsCommand.notificationAudience());
            assertEquals(expectedTitle, studentsCommand.title());
            assertEquals(expectedMessage, studentsCommand.message());
            assertEquals(expectedLink, studentsCommand.link());
            assertEquals(Priority.NORMAL, studentsCommand.priority());
            assertEquals(expectedData, studentsCommand.data());

            PushNotificationCommandDTO driverCommand = capturedCommands.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driverCommand.notificationAudience());
            assertEquals(travel.getDriver().getId(), driverCommand.userId());
            assertEquals(expectedTitle, driverCommand.title());
            assertEquals(expectedData, driverCommand.data());

            PushNotificationCommandDTO adminCommand = capturedCommands.get(2);
            assertEquals(NotificationAudience.ADMIN_ONLY, adminCommand.notificationAudience());
            assertEquals(travel.getCustomer().getId(), adminCommand.customerId());
            assertEquals(expectedTitle, adminCommand.title());
            assertEquals(expectedData, adminCommand.data());
        }

        @Test
        @DisplayName("Deve disparar três notificações do cliclo de start da viagem com os dados corretos")
        void shouldSendNotificationToStartTravel() {
            String expectedTitle = "Viagem iniciada";
            String expectedMessage = "A viagem do turno" + travel.getTravelPeriod() + "foi iniciada";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "TRAVEL_STARTED",
                    "travelId", travel.getId().toString(),
                    "period", travel.getTravelPeriod().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> commandCaptor = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            travelNotificationService.sendTravelStartedNotification(travel);

            verify(firebaseNotificationSender, times(3)).sendPushNotification(commandCaptor.capture());

            List<PushNotificationCommandDTO> capturedCommands = commandCaptor.getAllValues();

            PushNotificationCommandDTO studentsCommand = capturedCommands.getFirst();
            assertEquals(NotificationAudience.TRAVEL_STUDENTS, studentsCommand.notificationAudience());
            assertEquals(expectedTitle, studentsCommand.title());
            assertEquals(expectedMessage, studentsCommand.message());
            assertEquals(expectedLink, studentsCommand.link());
            assertEquals(Priority.NORMAL, studentsCommand.priority());
            assertEquals(expectedData, studentsCommand.data());

            PushNotificationCommandDTO driverCommand = capturedCommands.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driverCommand.notificationAudience());
            assertEquals(travel.getDriver().getId(), driverCommand.userId());
            assertEquals(expectedTitle, driverCommand.title());
            assertEquals(expectedData, driverCommand.data());

            PushNotificationCommandDTO adminCommand = capturedCommands.get(2);
            assertEquals(NotificationAudience.ADMIN_ONLY, adminCommand.notificationAudience());
            assertEquals(travel.getCustomer().getId(), adminCommand.customerId());
            assertEquals(expectedTitle, adminCommand.title());
            assertEquals(expectedData, adminCommand.data());
        }

        @Test
        @DisplayName("Deve disparar três notificações do cliclo de encerramento da viagem com os dados corretos")
        void shouldSendNotificationToEndTravel() {
            String expectedTitle = "Viagem finalizada";
            String expectedMessage = "A viagem do turno" + travel.getTravelPeriod() + "foi finalizada";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "TRAVEL_ENDED",
                    "travelId", travel.getId().toString(),
                    "period", travel.getTravelPeriod().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> commandCaptor = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            travelNotificationService.sendTravelEndedNotification(travel);

            verify(firebaseNotificationSender, times(3)).sendPushNotification(commandCaptor.capture());

            List<PushNotificationCommandDTO> capturedCommands = commandCaptor.getAllValues();

            PushNotificationCommandDTO studentsCommand = capturedCommands.getFirst();
            assertEquals(NotificationAudience.TRAVEL_STUDENTS, studentsCommand.notificationAudience());
            assertEquals(expectedTitle, studentsCommand.title());
            assertEquals(expectedMessage, studentsCommand.message());
            assertEquals(expectedLink, studentsCommand.link());
            assertEquals(Priority.NORMAL, studentsCommand.priority());
            assertEquals(expectedData, studentsCommand.data());

            PushNotificationCommandDTO driverCommand = capturedCommands.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driverCommand.notificationAudience());
            assertEquals(travel.getDriver().getId(), driverCommand.userId());
            assertEquals(expectedTitle, driverCommand.title());
            assertEquals(expectedData, driverCommand.data());

            PushNotificationCommandDTO adminCommand = capturedCommands.get(2);
            assertEquals(NotificationAudience.ADMIN_ONLY, adminCommand.notificationAudience());
            assertEquals(travel.getCustomer().getId(), adminCommand.customerId());
            assertEquals(expectedTitle, adminCommand.title());
            assertEquals(expectedData, adminCommand.data());
        }

        @Test
        @DisplayName("Deve disparar três notificações do cliclo de cancelamento da viagem com os dados corretos")
        void shouldSendNotificationToCanceledTravel() {
            String expectedTitle = "Viagem cancelada";
            String expectedMessage = "A viagem do turno" + travel.getTravelPeriod() + "foi cancelada";
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "TRAVEL_CANCELED",
                    "travelId", travel.getId().toString(),
                    "period", travel.getTravelPeriod().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> commandCaptor = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            travelNotificationService.sendTravelCanceledNotification(travel);

            verify(firebaseNotificationSender, times(3)).sendPushNotification(commandCaptor.capture());

            List<PushNotificationCommandDTO> capturedCommands = commandCaptor.getAllValues();

            PushNotificationCommandDTO studentsCommand = capturedCommands.getFirst();
            assertEquals(NotificationAudience.TRAVEL_STUDENTS, studentsCommand.notificationAudience());
            assertEquals(expectedTitle, studentsCommand.title());
            assertEquals(expectedMessage, studentsCommand.message());
            assertEquals(expectedLink, studentsCommand.link());
            assertEquals(Priority.NORMAL, studentsCommand.priority());
            assertEquals(expectedData, studentsCommand.data());

            PushNotificationCommandDTO driverCommand = capturedCommands.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driverCommand.notificationAudience());
            assertEquals(travel.getDriver().getId(), driverCommand.userId());
            assertEquals(expectedTitle, driverCommand.title());
            assertEquals(expectedData, driverCommand.data());

            PushNotificationCommandDTO adminCommand = capturedCommands.get(2);
            assertEquals(NotificationAudience.ADMIN_ONLY, adminCommand.notificationAudience());
            assertEquals(travel.getCustomer().getId(), adminCommand.customerId());
            assertEquals(expectedTitle, adminCommand.title());
            assertEquals(expectedData, adminCommand.data());
        }

        @Test
        @DisplayName("Deve disparar duas notificações do cliclo de troca de motorista da viagem com os dados corretos")
        void shouldSendNotificationToDriverChanged() {
            String expectedTitle = "Mudança de motorista";
            String expectedMessage = "Houve uma modificação no motorista da viagem do turno" + travel.getTravelPeriod() + ". Novo motorista: " + driver.getName();
            String expectedLink = "/travels/" + travel.getId() + "/tracking";

            Map<String, String> expectedData = Map.of(
                    "eventType", "DRIVER_CHANGED",
                    "travelId", travel.getId().toString(),
                    "period", travel.getTravelPeriod().toString()
            );

            ArgumentCaptor<PushNotificationCommandDTO> commandCaptor = ArgumentCaptor.forClass(PushNotificationCommandDTO.class);

            travelNotificationService.sendDriverChangedNotification(travel, driver);

            verify(firebaseNotificationSender, times(2)).sendPushNotification(commandCaptor.capture());

            List<PushNotificationCommandDTO> capturedCommands = commandCaptor.getAllValues();

            PushNotificationCommandDTO studentsCommand = capturedCommands.getFirst();
            assertEquals(NotificationAudience.TRAVEL_STUDENTS, studentsCommand.notificationAudience());
            assertEquals(expectedTitle, studentsCommand.title());
            assertEquals(expectedMessage, studentsCommand.message());
            assertEquals(expectedLink, studentsCommand.link());
            assertEquals(Priority.NORMAL, studentsCommand.priority());
            assertEquals(expectedData, studentsCommand.data());

            PushNotificationCommandDTO driverCommand = capturedCommands.get(1);
            assertEquals(NotificationAudience.SPECIFIC_USER, driverCommand.notificationAudience());
            assertEquals(travel.getDriver().getId(), driverCommand.userId());
            assertEquals(expectedTitle, driverCommand.title());
            assertEquals(expectedData, driverCommand.data());
        }
    }
}