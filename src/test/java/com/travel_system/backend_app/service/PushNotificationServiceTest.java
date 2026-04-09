package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentProximityEvents;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
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
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        clearInvocations(
                redisNotificationService,
                travelService,
                routeCalculationService,
                eventPublisher
        );
    }

    @Nested
    class checkProximityAlerts {

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

        @Test
        @DisplayName("should return silently when alert type equals state recovery")
        void shouldReturnSilentlyWhenAlertTypeEqualsStateRecovery() {

        }
    }
}