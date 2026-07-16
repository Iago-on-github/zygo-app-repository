package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentProximityEvents;
import com.travel_system.backend_app.events.VehicleMovementEvents;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.LastLocationDTO;
import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {
    @InjectMocks
    private PushNotificationService pushNotificationService;

    @Mock
    private TravelService travelService;
    @Mock
    private RouteCalculationService routeCalculationService;
    @Mock
    private RedisNotificationService redisNotificationService;
    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private LocationService locationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // clean the mocks in each runtime
        clearInvocations(
                redisNotificationService,
                travelService,
                routeCalculationService,
                eventPublisher
        );
    }

    @Nested
    class checkProximityAlerts {
        UUID travelId;
        UUID studentId;
        StudentTrackingPositionDTO studentTrackingPositionDTO;
        VehicleLocationRequestDTO vehicleLocRequest;
        StudentTravelResponseDTO studentTravelResponse;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentId = UUID.randomUUID();

            studentTrackingPositionDTO = new StudentTrackingPositionDTO(studentId, -12.9714, -38.5014);

            GeoPosition geoPosition = new GeoPosition();
            geoPosition.setLatitude(-12.9800);
            geoPosition.setLongitude(-38.5100);

            studentTravelResponse = new StudentTravelResponseDTO(UUID.randomUUID(), travelId, studentId, null, null, geoPosition);

            vehicleLocRequest = new VehicleLocationRequestDTO(travelId, -12.9714, -38.5014, 60.0, 180.0);
        }

        @Nested
        class throughTheMethodAndNotifyWithSuccess {

            @Test
            @DisplayName("Deve disparar primeiro alerta de proximidade (INITIAL_STATE) com sucesso")
            void shouldSendProximityAlertForInitialStateWithSuccess() {
                Double simulatedDistance = 500.0; // menor que 1000m -> Zona "NEAR"

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, simulatedDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(null);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(simulatedDistance),
                        isNull()
                )).thenReturn(true);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                ArgumentCaptor<StudentProximityEvents> eventCaptor = ArgumentCaptor.forClass(StudentProximityEvents.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

                StudentProximityEvents publishedEvent = eventCaptor.getValue();
                assertEquals(travelId, publishedEvent.travelId());
                assertEquals(studentId, publishedEvent.studentId());
                assertEquals(simulatedDistance, publishedEvent.distance());
                assertEquals("NEAR", publishedEvent.zone()); // 500m < 1000m
                assertEquals("INITIAL_STATE", publishedEvent.alertType()); // estado inicial
                assertNotNull(publishedEvent.timestamp());

                ArgumentCaptor<NotificationStateDTO> stateCaptor = ArgumentCaptor.forClass(NotificationStateDTO.class);
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), stateCaptor.capture());

                NotificationStateDTO savedState = stateCaptor.getValue();
                assertEquals("NEAR", savedState.zone());
                assertEquals(simulatedDistance.toString(), savedState.lastDistanceNotified());
                assertNotNull(savedState.lastNotificationAt());
                assertNotNull(savedState.timeStamp());
            }

            @Test
            @DisplayName("Deve disparar alerta de proximidade (ZONE_CHANGED) ao transicionar de FAR para NEAR")
            void shouldSendProximityAlertForZoneChangedWithSuccess() {
                Double currentDistance = 450.0;

                NotificationStateDTO previousState = new NotificationStateDTO(
                        "FAR",
                        "1500.0",
                        String.valueOf(System.currentTimeMillis() - 60000),
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(previousState);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(currentDistance),
                        eq(previousState)
                )).thenReturn(true);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                ArgumentCaptor<StudentProximityEvents> eventCaptor = ArgumentCaptor.forClass(StudentProximityEvents.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

                StudentProximityEvents publishedEvent = eventCaptor.getValue();
                assertEquals(travelId, publishedEvent.travelId());
                assertEquals(studentId, publishedEvent.studentId());
                assertEquals(currentDistance, publishedEvent.distance());
                assertEquals("NEAR", publishedEvent.zone());
                assertEquals("ZONE_CHANGED", publishedEvent.alertType());
                assertNotNull(publishedEvent.timestamp());

                ArgumentCaptor<NotificationStateDTO> stateCaptor = ArgumentCaptor.forClass(NotificationStateDTO.class);
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), stateCaptor.capture());

                NotificationStateDTO savedState = stateCaptor.getValue();
                assertEquals("NEAR", savedState.zone());
                assertEquals(currentDistance.toString(), savedState.lastDistanceNotified());
                assertNotNull(savedState.lastNotificationAt());
                assertNotNull(savedState.timeStamp());
            }

            @Test
            @DisplayName("Deve disparar alerta de proximidade (TIME_ELAPSED) quando o tempo de cooldown de 12 minutos expirar")
            void shouldSendProximityAlertForTimeElapsedWithSuccess() {
                Double currentDistance = 500.0;

                // estado anterior no Redis com um timestamp de 13 minutos atrás (780.000 ms), forçando o estouro do cooldown de 12 minutos
                long thirteenMinutesAgo = System.currentTimeMillis() - 780000;
                NotificationStateDTO previousState = new NotificationStateDTO(
                        "NEAR",
                        "500.0",
                        String.valueOf(thirteenMinutesAgo),
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(previousState);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(currentDistance),
                        eq(previousState)
                )).thenReturn(true);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                ArgumentCaptor<StudentProximityEvents> eventCaptor = ArgumentCaptor.forClass(StudentProximityEvents.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

                StudentProximityEvents publishedEvent = eventCaptor.getValue();
                assertEquals(travelId, publishedEvent.travelId());
                assertEquals(studentId, publishedEvent.studentId());
                assertEquals(currentDistance, publishedEvent.distance());
                assertEquals("NEAR", publishedEvent.zone());
                assertEquals("TIME_ELAPSED", publishedEvent.alertType());
                assertNotNull(publishedEvent.timestamp());

                ArgumentCaptor<NotificationStateDTO> stateCaptor = ArgumentCaptor.forClass(NotificationStateDTO.class);
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), stateCaptor.capture());

                NotificationStateDTO savedState = stateCaptor.getValue();
                assertEquals("NEAR", savedState.zone());
                assertEquals(currentDistance.toString(), savedState.lastDistanceNotified());
                assertNotNull(savedState.lastNotificationAt());
                assertNotNull(savedState.timeStamp());
            }

            @Test
            @DisplayName("Deve disparar alerta de proximidade (DISTANCE_STEP_REACHED) quando a distância percorrida atingir o passo estipulado")
            void shouldSendProximityAlertForDistanceStepReachedWithSuccess() {
                Double currentDistance = 500.0;

                NotificationStateDTO previousState = new NotificationStateDTO(
                        "NEAR",
                        "550.0",
                        String.valueOf(System.currentTimeMillis() - 60000),
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(previousState);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(currentDistance),
                        eq(previousState)
                )).thenReturn(true);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                ArgumentCaptor<StudentProximityEvents> eventCaptor = ArgumentCaptor.forClass(StudentProximityEvents.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

                StudentProximityEvents publishedEvent = eventCaptor.getValue();
                assertEquals(travelId, publishedEvent.travelId());
                assertEquals(studentId, publishedEvent.studentId());
                assertEquals(currentDistance, publishedEvent.distance());
                assertEquals("NEAR", publishedEvent.zone());
                assertEquals("DISTANCE_STEP_REACHED", publishedEvent.alertType());
                assertNotNull(publishedEvent.timestamp());

                ArgumentCaptor<NotificationStateDTO> stateCaptor = ArgumentCaptor.forClass(NotificationStateDTO.class);
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), stateCaptor.capture());

                NotificationStateDTO savedState = stateCaptor.getValue();
                assertEquals("NEAR", savedState.zone());
                assertEquals(currentDistance.toString(), savedState.lastDistanceNotified());
                assertNotNull(savedState.lastNotificationAt());
                assertNotNull(savedState.timeStamp());
            }

            @Test
            @DisplayName("Deve disparar alerta de proximidade (PERIODIC_UPDATE) quando autorizado pela verificação do Redis, mesmo sem estouro de cooldown ou limite de passo de distância")
            void shouldSendProximityAlertForPeriodicUpdateWithSuccess() {
                Double currentDistance = 500.0;

                long oneMinuteAgo = System.currentTimeMillis() - 60000;
                NotificationStateDTO previousState = new NotificationStateDTO(
                        "NEAR",
                        "510.0",
                        String.valueOf(oneMinuteAgo),
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(previousState);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(currentDistance),
                        eq(previousState)
                )).thenReturn(true);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                ArgumentCaptor<StudentProximityEvents> eventCaptor = ArgumentCaptor.forClass(StudentProximityEvents.class);
                verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

                StudentProximityEvents publishedEvent = eventCaptor.getValue();
                assertEquals(travelId, publishedEvent.travelId());
                assertEquals(studentId, publishedEvent.studentId());
                assertEquals(currentDistance, publishedEvent.distance());
                assertEquals("NEAR", publishedEvent.zone());
                assertEquals("PERIODIC_UPDATE", publishedEvent.alertType());
                assertNotNull(publishedEvent.timestamp());

                ArgumentCaptor<NotificationStateDTO> stateCaptor = ArgumentCaptor.forClass(NotificationStateDTO.class);
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), stateCaptor.capture());

                NotificationStateDTO savedState = stateCaptor.getValue();
                assertEquals("NEAR", savedState.zone());
                assertEquals(currentDistance.toString(), savedState.lastDistanceNotified());
                assertNotNull(savedState.lastNotificationAt());
                assertNotNull(savedState.timeStamp());
            }
        }

        @Nested
        class throughTheMethodAndReturnSilently {

            @Test
            @DisplayName("Deve retornar silenciosamente e não disparar alerta quando a distância do aluno não for calculada")
            void shouldReturnSilentlyWhenStudentDistanceIsNotCalculated() {

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of());

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                verify(eventPublisher, never()).publishEvent(any());
                verify(redisNotificationService, never()).updateNotificationState(any(), any(), any());
            }

            @Test
            @DisplayName("Deve retornar de forma silenciosa quando as regras do Redis bloquearem o envio da notificação")
            void shouldReturnSilentlyWhenRedisRulesBlockNotification() {
                Double currentDistance = 500.0;

                NotificationStateDTO previousState = new NotificationStateDTO(
                        "NEAR",
                        "500.0",
                        String.valueOf(System.currentTimeMillis() - 60000),
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(previousState);

                when(redisNotificationService.verifyNotificationState(
                        eq(travelId),
                        eq(studentId),
                        eq(currentDistance),
                        eq(previousState)
                )).thenReturn(false);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                verify(eventPublisher, never()).publishEvent(any());
                verify(redisNotificationService, never()).updateNotificationState(any(), any(), any());
            }

            @Test
            @DisplayName("Deve recuperar-se e redefinir o estado no Redis quando os dados armazenados estiverem corrompidos")
            void shouldRecoverAndResetRedisStateWhenDataIsCorrupt() {
                Double currentDistance = 500.0;

                NotificationStateDTO corruptState = new NotificationStateDTO(
                        "NEAR",
                        "500.0",
                        "STRING_CORROMPIDA_MOCK", // falha no Long.parseLong()
                        "2026-07-16T12:00:00Z"
                );

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTrackingPositionDTO));

                DistanceResponseDTO distanceResponse = new DistanceResponseDTO(studentId, currentDistance);
                when(locationService.distanceBetweenPositions(eq(travelId), any(LiveLocationDTO.class)))
                        .thenReturn(List.of(distanceResponse));

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(corruptState);

                pushNotificationService.checkProximityAlerts(vehicleLocRequest);

                verify(eventPublisher, never()).publishEvent(any());
                verify(redisNotificationService, times(1)).updateNotificationState(any(), any(), any());
            }
        }
    }

    @Nested
    class processVehicleMovement {

        private UUID travelId;
        private VehicleLocationRequestDTO vehicleLocRequest;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();

            // Coordenadas geográficas iniciais para simular a rota do veículo
            vehicleLocRequest = new VehicleLocationRequestDTO(
                    travelId,
                    -12.9714,
                    -38.5014,
                    60.0,
                    180.0
            );
        }

        @Test
        @DisplayName("Deve processar primeiro ping de localização da viagem com sucesso (Sem histórico no Redis)")
        void shouldProcessFirstPingWhenNoHistoryExistsInRedis() {
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(null);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(null);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.INSUFFICIENT_DATA, savedState.movementState());
            assertNotNull(savedState.stateStartedAt());
            assertNull(savedState.lastEtaNotificationAt());
            assertNull(savedState.lastNotificationSendAt());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(travelId, publishedEvent.travelId());
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());
            assertNotNull(publishedEvent.traceId());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.INSUFFICIENT_DATA, analysis.movementState());
            assertNull(analysis.averageSpeed());
            assertNull(analysis.timeElapsed());
            assertNull(analysis.distanceBetweenPings());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve retornar INSUFFICIENT_DATA silenciosamente quando o intervalo entre pings for inferior a 5 segundos (Spam de Requisição)")
        void shouldReturnInsufficientDataWhenPingIntervalIsLessThanFiveSeconds() {
            long threeSecondsAgo = System.currentTimeMillis() - 3000;

            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    threeSecondsAgo
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(null);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.INSUFFICIENT_DATA, savedState.movementState());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(travelId, publishedEvent.travelId());
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision()); // Não deve notificar spam

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.INSUFFICIENT_DATA, analysis.movementState());
            assertNull(analysis.averageSpeed());
            assertNull(analysis.timeElapsed());
            assertNull(analysis.distanceBetweenPings());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve retornar INSUFFICIENT_DATA quando o cálculo de Haversine falhar e retornar nulo")
        void shouldReturnInsufficientDataWhenHaversineCalculationReturnsNull() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(null);

            // Força o retorno nulo no cálculo de distância geodésica
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(null);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.INSUFFICIENT_DATA, savedState.movementState());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(travelId, publishedEvent.travelId());
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.INSUFFICIENT_DATA, analysis.movementState());
            assertNull(analysis.averageSpeed());
            assertNull(analysis.timeElapsed());
            assertNull(analysis.distanceBetweenPings());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve calcular velocidade normal, atualizar o ETA com sucesso no Redis e publicar o evento sem notificar")
        void shouldProcessNormalMovementAndUpdateEtaSuccessfully() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;

            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(60),
                    null,
                    null
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(100.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            verify(redisTrackingService, times(1)).updateTripEtaState(
                    eq(travelId),
                    any(Double.class),
                    any(Double.class),
                    any(Instant.class)
            );

            verify(redisTrackingService, times(1)).updateAccumulatedDistance(eq(travelId), eq(100.0));
            verify(redisTrackingService, times(1)).keepMemoryBetweenDriverPings(eq(travelId), any());

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());
            assertEquals(MovementState.NORMAL, movementStateCaptor.getValue().movementState());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.NORMAL, analysis.movementState());
            assertEquals(10.0, analysis.averageSpeed());
            assertEquals(10L, analysis.timeElapsed());
            assertEquals(100.0, analysis.distanceBetweenPings());
            assertEquals(50.0, analysis.newETA());
        }

        @Test
        @DisplayName("Deve transicionar de NORMAL para LENTO e salvar o estado no Redis sem disparar alerta imediato")
        void shouldTransitionFromNormalToSlowWithoutImmediateAlert() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(60),
                    null,
                    null
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            // Velocidade = 0.2 m/s (LENTO). Por ser <= 0.5 m/s, o novo ETA não é recalculado e retorna null
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(2.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.SLOW, savedState.movementState());
            assertNotNull(savedState.stateStartedAt());
            assertNull(savedState.lastNotificationSendAt());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.SLOW, analysis.movementState());
            assertEquals(0.2, analysis.averageSpeed());
            assertEquals(10L, analysis.timeElapsed());
            assertEquals(2.0, analysis.distanceBetweenPings());
            assertNull(analysis.newETA()); // CORREÇÃO: Sem recálculo de ETA para velocidades abaixo de 0.5 m/s
        }

        @Test
        @DisplayName("Deve disparar alerta de lentidão quando o estado LENTO for mantido por tempo superior a 4 segundos")
        void shouldNotifySlowMovementWhenDurationExceedsFourSeconds() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            // Define que o veículo já estava em estado LENTO desde 6 segundos atrás (estouro do limite de 4s)
            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.SLOW,
                    Instant.now().minusSeconds(6),
                    null,
                    null
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            // Velocidade = 0.2 m/s (LENTO)
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(2.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.SLOW, savedState.movementState());
            assertNotNull(savedState.lastNotificationSendAt());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NOTIFY_SLOW, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.SLOW, analysis.movementState());
            assertEquals(0.2, analysis.averageSpeed());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve bloquear envio de nova notificação de lentidão devido ao cooldown de 12 segundos")
        void shouldBlockSlowNotificationDueToTwelveSecondsCooldown() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            Instant stateStartedAt = Instant.now().minusSeconds(10);
            Instant recentNotificationTime = Instant.now().minusSeconds(5); // Cooldown ativo (menor que 12s)

            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.SLOW,
                    stateStartedAt,
                    recentNotificationTime,
                    recentNotificationTime
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(2.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            verify(redisTrackingService, never()).saveAnalyzedMovementState(eq(travelId), any());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.SLOW, analysis.movementState());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve transicionar de NORMAL para PARADO e salvar o novo estado no Redis sem disparar alerta imediato")
        void shouldTransitionFromNormalToStoppedWithoutImmediateAlert() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(60),
                    null,
                    null
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            // distância de 0.0 metros define o estado como STOPPED
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(0.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.STOPPED, savedState.movementState());
            assertNotNull(savedState.stateStartedAt());
            assertNull(savedState.lastNotificationSendAt());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.STOPPED, analysis.movementState());
            assertEquals(0.0, analysis.averageSpeed());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve disparar alerta de parada quando o veículo permanecer parado por mais de 4 segundos")
        void shouldNotifyStoppedMovementWhenDurationExceedsFourSeconds() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            // Veículo já estava em estado STOPPED desde 8 segundos atrás
            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.now().minusSeconds(8),
                    null,
                    null
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(0.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            ArgumentCaptor<AnalyzeMovementStateDTO> movementStateCaptor = ArgumentCaptor.forClass(AnalyzeMovementStateDTO.class);
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(eq(travelId), movementStateCaptor.capture());

            AnalyzeMovementStateDTO savedState = movementStateCaptor.getValue();
            assertEquals(MovementState.STOPPED, savedState.movementState());
            assertNotNull(savedState.lastNotificationSendAt());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NOTIFY_STOPPED, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.STOPPED, analysis.movementState());
            assertEquals(0.0, analysis.averageSpeed());
            assertNull(analysis.newETA());
        }

        @Test
        @DisplayName("Deve bloquear envio de nova notificação de parada devido ao cooldown de 5 minutos")
        void shouldBlockStoppedNotificationDueToFiveMinutesCooldown() {
            long tenSecondsAgo = System.currentTimeMillis() - 10000;
            LastLocationDTO lastLocation = new LastLocationDTO(
                    -12.9714,
                    -38.5014,
                    tenSecondsAgo
            );

            Instant stateStartedAt = Instant.now().minusSeconds(310);
            Instant recentNotificationTime = Instant.now().minusSeconds(120); // 2 minutos atrás (dentro do cooldown de 5 minutos)

            AnalyzeMovementStateDTO previousMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    stateStartedAt,
                    recentNotificationTime,
                    recentNotificationTime
            );

            com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO previousEta =
                    new com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO(120.0, 500.0, 1721136000000L);

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocation);
            when(redisTrackingService.getLastMovementState(travelId)).thenReturn(previousMovementState);
            when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousEta);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocRequest.latitude()),
                    eq(vehicleLocRequest.longitude()),
                    eq(lastLocation.latitude()),
                    eq(lastLocation.longitude())
            )).thenReturn(0.0);

            pushNotificationService.processVehicleMovement(vehicleLocRequest);

            verify(redisTrackingService, never()).saveAnalyzedMovementState(eq(travelId), any());

            ArgumentCaptor<VehicleMovementEvents> eventCaptor = ArgumentCaptor.forClass(VehicleMovementEvents.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

            VehicleMovementEvents publishedEvent = eventCaptor.getValue();
            assertEquals(ShouldNotify.SHOULD_NO_NOTIFY, publishedEvent.decision());

            VelocityAnalysisDTO analysis = publishedEvent.velocityAnalysis();
            assertNotNull(analysis);
            assertEquals(MovementState.STOPPED, analysis.movementState());
            assertEquals(0.0, analysis.averageSpeed());
            assertNull(analysis.newETA());
        }
    }
}