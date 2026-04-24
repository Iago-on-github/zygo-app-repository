package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentProximityEvents;
import com.travel_system.backend_app.events.VehicleMovementEvents;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

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

        @Nested
        class throughTheMethodAndNotifyWithSuccess {
            @Test
            @DisplayName("should publish event when alert type equals initial state with success")
            void shouldPublishEventWhenAlertTypeEqualsInitialState() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.9800);
                geoPosition.setLongitude(-38.5100);

                StudentTravelResponseDTO student = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(null);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), isNull())).thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(student));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(500.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(eventPublisher, times(1)).publishEvent((Object) argThat(rawEvent -> {
                    if (!(rawEvent instanceof StudentProximityEvents e)) return false;
                    return e.travelId().equals(travelId) &&
                            e.studentId().equals(studentId) &&
                            e.alertType().equals("INITIAL_STATE");
                }));

                verify(redisNotificationService, times(1)).updateNotificationState(
                        eq(travelId),
                        eq(studentId),
                        any(NotificationStateDTO.class)
                );

            }

            @Test
            @DisplayName("should push event when alert type equals 'zone changed'")
            void shouldPublishEventWhenAlertTypeEqualsZoneChanged() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", "1775692270779", "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(1500.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(eventPublisher, times(1))
                        .publishEvent((Object) argThat(event -> {
                            if (!(event instanceof StudentProximityEvents e)) return false;
                            return e.travelId().equals(travelId) &&
                                    e.studentId().equals(studentId) &&
                                    e.alertType().equals("ZONE_CHANGED");

                        }));

                verify(redisNotificationService, times(1))
                        .updateNotificationState(
                                eq(travelId),
                                eq(studentId),
                                argThat(state -> state.zone().equals("FAR") &&
                                        state.lastDistanceNotified().equals("1500.0"))
                        );

            }

            @Test
            @DisplayName("should publish event when alert type equals 'time elapsed'")
            void shouldPublishEventWhenAlertTypeEqualsTimeElapsed() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                // garante que seja sempre >= 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().minusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(500.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(eventPublisher, times(1))
                        .publishEvent((Object) argThat(event -> {
                            if (!(event instanceof StudentProximityEvents e)) return false;
                            return e.travelId().equals(travelId) &&
                                    e.studentId().equals(studentId) &&
                                    e.alertType().equals("TIME_ELAPSED");
                        }));

                verify(redisNotificationService, times(1))
                        .updateNotificationState(
                                eq(travelId),
                                eq(studentId),
                                argThat(state -> state.zone().equals("NEAR") &&
                                        state.lastDistanceNotified().equals("500.0"))
                        );
            }

            @Test
            @DisplayName("should publish event when alert type equals distance step reached")
            void shouldPublishEventWhenAlertTypeEqualsDistanceStepReached() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                // garante que seja sempre menor 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().plusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(560.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(eventPublisher, times(1))
                        .publishEvent((Object) argThat(event -> {
                            if (!(event instanceof StudentProximityEvents e)) return false;
                            return e.travelId().equals(travelId) &&
                                    e.studentId().equals(studentId) &&
                                    e.alertType().equals("DISTANCE_STEP_REACHED");
                        }));

                verify(redisNotificationService, times(1))
                        .updateNotificationState(
                                eq(travelId),
                                eq(studentId),
                                argThat(state -> state.zone().equals("NEAR") &&
                                        state.lastDistanceNotified().equals("560.0"))
                        );
            }

            @Test
            @DisplayName("should publish event when alert type equals periodic update")
            void shouldPublishEventWhenAlertTypeEqualsPeriodicUpdate() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                // garante que seja sempre menor 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().plusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(500.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(eventPublisher, times(1))
                        .publishEvent((Object) argThat(event -> {
                            if (!(event instanceof StudentProximityEvents e)) return false;
                            return e.travelId().equals(travelId) &&
                                    e.studentId().equals(studentId) &&
                                    e.alertType().equals("PERIODIC_UPDATE");
                        }));

                verify(redisNotificationService, times(1))
                        .updateNotificationState(
                                eq(travelId),
                                eq(studentId),
                                argThat(state -> state.zone().equals("NEAR") &&
                                        state.lastDistanceNotified().equals("500.0"))
                        );
            }
        }

        @Nested
        class throughTheMethodAndReturnSilently {
            @Test
            @DisplayName("should return silently when alert type equals state recovery")
            void shouldReturnSilentlyWhenAlertTypeEqualsStateRecovery() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", "corrupeted_data", "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);
                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(true);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(500.0);

                // act
                assertDoesNotThrow(() -> pushNotificationService.checkProximityAlerts(request));

                // assert
                verify(redisNotificationService, times(1))
                        .updateNotificationState(eq(travelId), eq(studentId), argThat(state -> state.zone().equals("NEAR") &&
                                state.lastDistanceNotified().equals("500.0") &&
                                state.timeStamp() != null));

                verify(eventPublisher, never()).publishEvent(any());
            }

            @Test
            @DisplayName("should return silently when there is no calculated distance for the current student")
            void shouldReturnSilentlyWhenThereIsNoCalculatedDistanceForTheStudent() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        null
                );

                // garante que seja sempre menor 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().plusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(redisNotificationService, never()).updateNotificationState(any(), any(), any());
                verify(eventPublisher, never()).publishEvent(any());
                verifyNoInteractions(routeCalculationService);
            }

            @Test
            @DisplayName("shouldn't publish event and/or call's redis update method when VerifyNotificationState returns false")
            void shouldNotPublishEventAndCallRedisUpdateMethodWhenVerifyNotificationStateReturnFalse() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                // garante que seja sempre menor 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().plusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(redisNotificationService.verifyNotificationState(eq(travelId), eq(studentId), anyDouble(), eq(readNotificationState)))
                        .thenReturn(false);
                when(redisNotificationService.readNotificationState(travelId, studentId)).thenReturn(readNotificationState);

                when(travelService.linkedStudentTravel(travelId)).thenReturn(Set.of(studentTravelResponseDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(500.0);

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(redisNotificationService, times(1)).verifyNotificationState(any(), any(), anyDouble(), any());

                verify(eventPublisher, never()).publishEvent(any());
                verify(redisNotificationService, never()).updateNotificationState(any(), any(), any());
            }

            @Test
            @DisplayName("should do nothing when linkedStudentTravel is empty")
            void shouldDoNothingWhenLinkedStudentTravelIsEmpty() {
                // arrange
                UUID travelId = UUID.randomUUID();
                UUID studentId = UUID.randomUUID();

                VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                        travelId, -12.9714, -38.5014, 60.0, 180.0
                );

                GeoPosition geoPosition = new GeoPosition();
                geoPosition.setLatitude(-12.013);
                geoPosition.setLongitude(-39.291);

                StudentTravelResponseDTO studentTravelResponseDTO = new StudentTravelResponseDTO(
                        UUID.randomUUID(),
                        travelId,
                        studentId,
                        null,
                        null,
                        geoPosition
                );

                // garante que seja sempre menor 12 minutos
                String lastNotificationAt = String.valueOf(Instant.now().plusSeconds(1800).toEpochMilli());

                NotificationStateDTO readNotificationState = new NotificationStateDTO("NEAR", "500.0", lastNotificationAt, "2026-04-08T23:51:10.779192300Z");

                when(travelService.linkedStudentTravel(travelId)).thenReturn(new HashSet<>());

                // act
                pushNotificationService.checkProximityAlerts(request);

                // asserts
                verify(redisNotificationService, never()).verifyNotificationState(any(), any(), anyDouble(), any());

                verify(eventPublisher, never()).publishEvent(any());
                verify(redisNotificationService, never()).updateNotificationState(any(), any(), any());
            }
        }
    }

    @Nested
    class processVehicleMovement {

        @Test
        @DisplayName("should set ShouldNotify with 'SHOULD_NO_NOTIFY' when last location not found from redis")
        void shouldSetShouldNotifyWithNoNotifyFlagWhenLastLocationNotFoundFromRedis() {
            // arrange
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 60.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(null);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).getLiveLocation(any());
            verify(redisTrackingService, times(1)).getLastLocation(travelId);

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.INSUFFICIENT_DATA) &&
                        v.velocityAnalysis().averageSpeed() == null &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should set ShouldNotify with 'SHOULD_NO_NOTIFY' when elapsed seconds less than min seconds")
        void shouldSetShouldNotifyWithNoNotifyFlagWhenElapsedSecondsLessThanMinSeconds() {
            // arrange
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 60.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            // timestamp deve ser menor que o min seconds
            long timestampLessThanMinSeconds = Instant.now().minusSeconds(2).toEpochMilli();
            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestampLessThanMinSeconds
            );

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).getLiveLocation(any());
            verify(redisTrackingService, times(1)).getLastLocation(any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.INSUFFICIENT_DATA) &&
                        v.velocityAnalysis().averageSpeed() == null &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should set ShouldNotify with 'SHOULD_NOTIFY_STOPPED' when distance between pings less than solid speed")
        void shouldMarkStateWithStoppedAndShouldNotifyWithFlagStopped() {
            // arrange
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 60.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            // timestamp hardcoded como 33, valor será em décadas - passa na validação.
            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    33L
            );

            PreviousStateDTO previousState = new PreviousStateDTO(
                    480.5,
                    1250.75,
                    1712683200000L
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.8);

            when(redisTrackingService.getPreviousEta(travelId.toString())).thenReturn(previousState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).getLiveLocation(any());
            verify(redisTrackingService, times(1)).getLastLocation(any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.STOPPED) &&
                        v.velocityAnalysis().averageSpeed() != null &&
                        v.decision().equals(ShouldNotify.SHOULD_NOTIFY_STOPPED);
            }));
        }

        @Test
        @DisplayName("should set Should Notify with 'SHOULD_NOTIFY_SLOW' when avg speed less than solid speed")
        void shouldMarkStateWithStoppedAndShouldNotifyWithFlagSlow() {
            // arrange
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 10.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            PreviousStateDTO previousState = new PreviousStateDTO(
                    480.5,
                    1250.75,
                    1712683200000L
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.SLOW,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(2.0);

            when(redisTrackingService.getPreviousEta(travelId.toString())).thenReturn(previousState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).getLiveLocation(any());
            verify(redisTrackingService, times(1)).getLastLocation(any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.SLOW) &&
                        v.velocityAnalysis().averageSpeed() != null &&
                        v.decision().equals(ShouldNotify.SHOULD_NOTIFY_SLOW);
            }));
        }

        @Test
        @DisplayName("should set Should Notify with 'SHOULD_NO_NOTIFY' when the bus' speed normal")
        void shouldMarkStateWithNormalAndShouldNoNotify() {
            // arrange
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            PreviousStateDTO previousState = new PreviousStateDTO(
                    480.5,
                    1250.75,
                    1712683200000L
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(23.0);

            when(redisTrackingService.getPreviousEta(travelId.toString())).thenReturn(previousState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).getLiveLocation(any());
            verify(redisTrackingService, times(1)).getLastLocation(any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.NORMAL) &&
                        v.velocityAnalysis().averageSpeed() != null &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));

            verify(redisTrackingService, times(1)).updateTripEtaState(
                    eq(travelId),
                    eq(previousState.distanceRemaining()),
                    anyDouble(),
                    any(Instant.class));
        }

        @Test
        @DisplayName("should never call update trip eta state when previous eta is null")
        void shouldNeverCallUpdateTripEtaStateWhenPreviousEtaIsNull() {
            // arrange
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(23.0);

            when(redisTrackingService.getPreviousEta(travelId.toString())).thenReturn(null);

            // act
            pushNotificationService.processVehicleMovement(request);

            verify(redisTrackingService, never()).updateTripEtaState(any(), anyDouble(), anyDouble(), any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.NORMAL) &&
                        v.velocityAnalysis().averageSpeed() != null &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should never recalculate eta if distance remaining less than or equals zero")
        void shouldNeverRecalculateEtaIfDistanceRemainingLessThanOrEqualsZero() {
            // arrange
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            PreviousStateDTO previousState = new PreviousStateDTO(
                    480.5,
                    0.0,
                    1712683200000L
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);
            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(23.0);

            when(redisTrackingService.getPreviousEta(travelId.toString())).thenReturn(previousState);

            // act
            pushNotificationService.processVehicleMovement(request);

            verify(redisTrackingService, never()).updateTripEtaState(any(), anyDouble(), anyDouble(), any());

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.NORMAL) &&
                        v.velocityAnalysis().averageSpeed() != null &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should call saveAnalyzedMovementState and return 'SHOULD_NO_NOTIFY' when lastMovementState is null")
        void shouldCallSaveAnalyzedMovementStateAndReturnNoNotifyWhenLastMovementStateIsNull() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance less than 1 = stopped
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.5);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(null);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyzer -> analyzer.movementState().equals(MovementState.STOPPED) &&
                            analyzer.stateStartedAt() != null &&
                            analyzer.lastEtaNotificationAt() == null &&
                            analyzer.lastNotificationSendAt() == null)
            );
        }

        @Test
        @DisplayName("if the actual state differs from redis the state, the method should save without throwing a notification")
        void shouldReturnNoNotifyIfStateChangedAndSaveOnRedis() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.NORMAL,
                    Instant.now().minusSeconds(10),
                    null,
                    null
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance less than 1 = stopped
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.5);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, times(1)).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyze -> analyze.movementState().equals(MovementState.STOPPED) &&
                            analyze.stateStartedAt() != null &&
                            analyze.lastNotificationSendAt() == null &&
                            analyze.lastEtaNotificationAt() == null)
            );

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.STOPPED) &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should no notify when stayed long enough are false even if state are slow")
        void shouldNoNotifyWhenStayedLongEnoughAreFalseEvenIfStateAreSlow() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.SLOW,
                    Instant.now(),
                    null,
                    null
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance more than 1 = slow
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(1.1);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, never()).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyze -> analyze.movementState().equals(MovementState.SLOW) &&
                            analyze.stateStartedAt() != null &&
                            analyze.lastNotificationSendAt() == null &&
                            analyze.lastEtaNotificationAt() == null)
            );

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.SLOW) &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should no notify when stayed long enough are false even if state are stopped")
        void shouldNoNotifyWhenStayedLongEnoughAreFalseEvenIfStateAreStopped() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.now(),
                    null,
                    null
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance less than 1 = stopped
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.5);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, never()).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyze -> analyze.movementState().equals(MovementState.STOPPED) &&
                            analyze.stateStartedAt() != null &&
                            analyze.lastNotificationSendAt() == null &&
                            analyze.lastEtaNotificationAt() == null)
            );

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.STOPPED) &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should no notify when lastEtaNotifyAt are recently")
        void shouldNoNotifyWhenLastEtaNotifyAtAreRecently() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.now(),
                    null,
                    Instant.now().minusSeconds(10) // less than 12
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance less than 1 = stopped
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.5);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, never()).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyze -> analyze.movementState().equals(MovementState.STOPPED) &&
                            analyze.stateStartedAt() != null &&
                            analyze.lastNotificationSendAt() == null &&
                            analyze.lastEtaNotificationAt() == null)
            );

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.STOPPED) &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }

        @Test
        @DisplayName("should return no notify when stay long enough is true, but stopped cooldown not expired yet")
        void shouldReturnNoNotifyWhenStayLongEnoughIsTrueButStoppedCooldownNotExpired() {
            UUID travelId = UUID.randomUUID();

            VehicleLocationRequestDTO request = new VehicleLocationRequestDTO(
                    travelId, -12.9714, -38.5014, 20.0, 180.0
            );

            long timestamp = Instant.now().minusSeconds(10).toEpochMilli();

            LastLocationDTO lastLocationDTO = new LastLocationDTO(
                    -32.932,
                    -73.133,
                    timestamp
            );

            AnalyzeMovementStateDTO lastMovementState = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.now().minusSeconds(10),
                    null,
                    Instant.now()
            );

            LiveLocationDTO liveLocation = new LiveLocationDTO(
                    -12.2674,
                    -38.9663,
                    "POINT(-38.9663 -12.2674)",
                    152.75,
                    -12.2669,
                    -38.9658
            );

            when(redisTrackingService.getLastLocation(travelId)).thenReturn(lastLocationDTO);
            when(redisTrackingService.getLiveLocation(travelId.toString())).thenReturn(liveLocation);

            // distance less than 1 = stopped
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.5);
            when(redisTrackingService.getLastMovementState(travelId.toString())).thenReturn(lastMovementState);

            // act
            pushNotificationService.processVehicleMovement(request);

            // asserts
            verify(redisTrackingService, never()).saveAnalyzedMovementState(
                    eq(travelId),
                    argThat(analyze -> analyze.movementState().equals(MovementState.STOPPED) &&
                            analyze.stateStartedAt() != null &&
                            analyze.lastNotificationSendAt() == null &&
                            analyze.lastEtaNotificationAt() == null)
            );

            verify(eventPublisher, times(1)).publishEvent((Object) argThat(event -> {
                if (!(event instanceof VehicleMovementEvents v)) return false;
                return v.travelId().equals(travelId) &&
                        v.velocityAnalysis().movementState().equals(MovementState.STOPPED) &&
                        v.decision().equals(ShouldNotify.SHOULD_NO_NOTIFY);
            }));
        }


    }
}