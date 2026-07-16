package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.LastLocationDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.rmi.server.UID;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTrackingServiceTest {

    private RedisTrackingService redisTrackingService;

    @Mock
    private RouteCalculationService routeCalculationService;

    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations hashOperations;

    private UUID travelId;

    private String routeKey;
    private String trackingKey;
    private String activeTravelKey;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        redisTrackingService = new RedisTrackingService(routeCalculationService, redisTemplate);

        travelId = UUID.randomUUID();
        routeKey = "travel:route:" + travelId;
        trackingKey = "travel:tracking:" + travelId;
        activeTravelKey = "ACTIVE_TRAVELS_KEY";

    }

    @Nested
    class storeCalculatedRouteState {

        @Test
        void shouldStoreCalculatedRouteStateWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String latitude = "-32.932";
            String longitude = "-12.402";
            Double distance = 392.12;
            String geometry = "geometry_teste";

            String routeKey = "travel:route:" + travelId;

            redisTrackingService.storeCalculatedRouteState(travelId, latitude, longitude, new RouteDetailsDTO(null, distance, geometry));

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> savedMap = captorMap.getValue();

            assertEquals(latitude, savedMap.get("last_calc_lat"));
            assertEquals(longitude, savedMap.get("last_calc_lng"));

            assertEquals("392.12", savedMap.get("distanceRemaining"));
            assertEquals("geometry_teste", savedMap.get("geometry"));
        }

        @Test
        @DisplayName("should store without optional properties like geometry or distance")
        void shouldStoreCalculatedRouteStateWithoutOptionalPropertiesWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String latitude = "-32.932";
            String longitude = "-12.402";

            String routeKey = "travel:route:" + travelId;

            redisTrackingService.storeCalculatedRouteState(travelId, latitude, longitude, new RouteDetailsDTO(null, null, null));

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> savedMap = captorMap.getValue();

            assertEquals(latitude, savedMap.get("last_calc_lat"));
            assertEquals(longitude, savedMap.get("last_calc_lng"));

            assertNull(savedMap.get("distanceRemaining"));
            assertNull(savedMap.get("geometry"));
        }

        @ParameterizedTest
        @MethodSource("nullParametersProvider")
        void throwExceptionWhenRequireParametersAreNull(UUID travelId, String calculationLatitude, String calculationLongitude) {
            redisTrackingService.storeCalculatedRouteState(travelId, calculationLatitude, calculationLongitude, null);

            verifyNoInteractions(hashOperations);
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(null, "-12.234", "-13.242"),
                    Arguments.of(UUID.randomUUID(), null, "-13.242"),
                    Arguments.of(UUID.randomUUID(), "-12.234", null)
            );
        }
    }

    @Nested
    class UpdateAccumulatedDistance {

        @Test
        void shouldUpdateAccumulatedDistanceWithSuccess() {
            when(hashOperations.get(eq(routeKey), eq("accumulatedDistance"))).thenReturn("100.0");

            redisTrackingService.updateAccumulatedDistance(travelId, 25.0);

            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

            verify(hashOperations, times(1)).put(eq(routeKey), eq("accumulatedDistance"), valueCaptor.capture());

            String storedValue = valueCaptor.getValue();

            assertNotNull(storedValue);
            assertEquals("125.0", storedValue);
        }

        @Test
        void shouldReturnSilentlyWhenErrorOccurs() {
            when(hashOperations.get(eq(routeKey), eq("accumulatedDistance"))).thenReturn("invalid_value");

            redisTrackingService.updateAccumulatedDistance(travelId, 25.0);

            verify(hashOperations, never()).put(anyString(), anyString(), anyString());
        }

        @ParameterizedTest
        @MethodSource("nullRequireParameterProvider")
        void shouldReturnSilentlyWhenRequireParametersAreNull(UUID travelId, Double incrementalDistance) {
            redisTrackingService.updateAccumulatedDistance(travelId, incrementalDistance);

            verifyNoInteractions(hashOperations);
        }

        public static Stream<Arguments> nullRequireParameterProvider() {
            return Stream.of(
                    Arguments.of(null, 100.0),
                    Arguments.of(UUID.randomUUID(), null)
            );
        }
    }

    @Nested
    class storeCurrentLocation {

        @Test
        void shouldStoreCurrentLocationWithSuccess() {
            redisTrackingService.storeCurrentLocation(travelId, new CurrentVehicleLocationDTO(-11.34, -12.234, 56.43, 22.1));

            ArgumentCaptor<Map<String, String>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(trackingKey), mapArgumentCaptor.capture());
            Map<String, String> storedValue = mapArgumentCaptor.getValue();

            assertNotNull(storedValue);

            assertEquals("-11.34", storedValue.get("current_lat"));
            assertEquals("-12.234", storedValue.get("current_lng"));

            assertEquals("56.43", storedValue.get("current_speed"));
            assertEquals("22.1", storedValue.get("current_heading"));

            assertNotNull(storedValue.get("current_location_timestamp"));

        }

        @Test
        @DisplayName("should store with success even when optional data no passed")
        void shouldStoreCurrentLocationWhenOptionalDataIsNotPassed() {
            redisTrackingService.storeCurrentLocation(travelId, new CurrentVehicleLocationDTO(-11.34, -12.234, null, null));

            ArgumentCaptor<Map<String, String>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(trackingKey), mapArgumentCaptor.capture());
            Map<String, String> storedValue = mapArgumentCaptor.getValue();

            assertNotNull(storedValue);

            assertEquals("-11.34", storedValue.get("current_lat"));
            assertEquals("-12.234", storedValue.get("current_lng"));

            assertNull(storedValue.get("current_speed"));
            assertNull(storedValue.get("current_heading"));

            assertNotNull(storedValue.get("current_location_timestamp"));
        }

        @ParameterizedTest
        @MethodSource("nullParametersProvider")
        void shouldReturnSilentlyWhenRequireParametersAreNull(UUID travelId, CurrentVehicleLocationDTO currentVehicleLocation) {
            redisTrackingService.storeCurrentLocation(travelId, currentVehicleLocation);

            verifyNoInteractions(hashOperations);
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(null, new CurrentVehicleLocationDTO(-11.34, -12.234, null, null)),
                    Arguments.of(UUID.randomUUID(), new CurrentVehicleLocationDTO(null, -12.234, null, null)),
                    Arguments.of(UUID.randomUUID(), new CurrentVehicleLocationDTO(-11.34, null, null, null)),
                    Arguments.of(UUID.randomUUID(), new CurrentVehicleLocationDTO(null, null, null, null))
            );
        }
    }

    @Nested
    class getAccumulatedDistance {

        @Test
        @DisplayName("should return accumulated distance stored from redis with success")
        void shouldReturnAccumulatedDistanceWithSuccess() {
            String expectedAccumulatedDist = "50.0";

            when(hashOperations.get(eq(routeKey), eq("accumulatedDistance"))).thenReturn(expectedAccumulatedDist);

            String result = redisTrackingService.getAccumulatedDistance(travelId);

            verify(hashOperations, times(1)).get(any(), any());

            assertEquals("50.0", result);
        }

        @Test
        @DisplayName("should return zero when accumulated distance are null from redis")
        void shouldReturnZeroWhenAccumulatedDistanceAreNull() {
            when(hashOperations.get(eq(routeKey), eq("accumulatedDistance"))).thenReturn(null);

            String result = redisTrackingService.getAccumulatedDistance(travelId);

            verify(hashOperations, times(1)).get(eq(routeKey), eq("accumulatedDistance"));

            assertEquals("0.0", result);
        }

        @Test
        @DisplayName("should return silently when travel id is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            String result = redisTrackingService.getAccumulatedDistance(null);

            verify(hashOperations, never()).get(any(), any());

            assertNull(result);
        }
    }

    @Nested
    class getCurrentLocation {

        @Test
        void shouldGetCurrentLocationWithSuccess() {
            Map<String, String> redisData = new HashMap<>();

            redisData.put("current_lat", "-23.55");
            redisData.put("current_lng", "-46.63");
            redisData.put("current_speed", "80.0");
            redisData.put("current_heading", "180.0");

            when(hashOperations.entries(eq(trackingKey))).thenReturn(redisData);

            CurrentVehicleLocationDTO result = redisTrackingService.getCurrentLocation(travelId);

            assertNotNull(result);
            assertEquals(-23.55, result.latitude());
            assertEquals(-46.63, result.longitude());
            assertEquals(80.0, result.speed());
            assertEquals(180.0, result.heading());
        }

        @Test
        @DisplayName("when data not found, should return null silently")
        void shouldReturnSilentlyWhenNotFoundAnyDataInRedis() {
            when(hashOperations.entries(eq(trackingKey))).thenReturn(null);

            CurrentVehicleLocationDTO result = redisTrackingService.getCurrentLocation(travelId);

            assertNull(result);
        }

        @ParameterizedTest
        @MethodSource("nullFieldsProvider")
        void shouldReturnSilentlyWhenNoneHaveStoredValueOfLatitudeOrLongitudeData() {
            Map<String, String> redisData = new HashMap<>();

            redisData.put("current_lat", null);
            redisData.put("current_lng", null);

            when(hashOperations.entries(eq(trackingKey))).thenReturn(redisData);

            CurrentVehicleLocationDTO result = redisTrackingService.getCurrentLocation(travelId);

            assertNull(result);
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, "-12.422"),
                    Arguments.of("-11.32", null),
                    Arguments.of(null, null)
            );
        }

        @Test
        void shouldReturnSilentlyWhenErrorOccursDuringDataProcessing() {
            Map<String, String> redisData = new HashMap<>();

            redisData.put("current_lat", "Invalid_Data");
            redisData.put("current_lng", "-46.63");
            redisData.put("current_speed", "80.0");
            redisData.put("current_heading", "180.0");

            when(hashOperations.entries(eq(trackingKey))).thenReturn(redisData);

            CurrentVehicleLocationDTO result = redisTrackingService.getCurrentLocation(travelId);

            assertNull(result);
        }
    }

    @Nested
    class getRouteState {

        @Test
        void shouldGetRouteStateWithSuccess() {
            Map<String, String> redisData = new HashMap<>();
            redisData.put("durationRemaining", "12.43");
            redisData.put("distanceRemaining", "700.3");
            redisData.put("geometry", "encoded_polyline_exemple");

            when(hashOperations.entries(routeKey)).thenReturn(redisData);

            RouteDetailsDTO result = redisTrackingService.getRouteState(travelId);

            assertNotNull(result);

            assertEquals(12.43, result.duration());
            assertEquals(700.3, result.distance());
            assertEquals("encoded_polyline_exemple", result.geometry());
        }

        @Test
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            RouteDetailsDTO result = redisTrackingService.getRouteState(null);

            assertNull(result);

            verifyNoInteractions(hashOperations);
        }

        @Test
        @DisplayName("when data not found, should return null silently")
        void shouldReturnSilentlyWhenNotFoundAnyDataInRedis() {
            when(hashOperations.entries(eq(routeKey))).thenReturn(null);

            RouteDetailsDTO result = redisTrackingService.getRouteState(travelId);

            assertNull(result);
        }

        @Test
        void shouldReturnSilentlyWhenErrorOccursDuringDataProcessing() {
            Map<String, String> redisData = new HashMap<>();

            redisData.put("durationRemaining", "Invalid_Data");
            redisData.put("distanceRemaining", "46.63");
            redisData.put("geometry", "encoded_polyline");

            when(hashOperations.entries(eq(routeKey))).thenReturn(redisData);

            RouteDetailsDTO result = redisTrackingService.getRouteState(travelId);

            assertNull(result);
        }
    }

    @Nested
    class getRouteCalculateReference {

        @Test
        void shouldGetRouteCalculateReferenceWithSuccess() {
            Map<String, String> redisData = new HashMap<>();
            redisData.put("last_calc_lat", "-12.123");
            redisData.put("last_calc_lng", "-13.206");

            when(hashOperations.entries(routeKey)).thenReturn(redisData);

            RouteCalculationReferenceDTO result = redisTrackingService.getRouteCalculateReference(travelId);

            assertNotNull(result);

            assertEquals(-12.123, result.lastCalcLat());
            assertEquals(-13.206, result.lastCalcLng());
        }

        @Test
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            RouteCalculationReferenceDTO result = redisTrackingService.getRouteCalculateReference(null);

            assertNull(result);

            verifyNoInteractions(hashOperations);
        }

        @Test
        @DisplayName("when data not found, should return null silently")
        void shouldReturnSilentlyWhenNotFoundAnyDataInRedis() {
            when(hashOperations.entries(eq(routeKey))).thenReturn(null);

            RouteCalculationReferenceDTO result = redisTrackingService.getRouteCalculateReference(travelId);

            assertNull(result);
        }

        @Test
        void shouldReturnSilentlyWhenErrorOccursDuringDataProcessing() {
            Map<String, String> redisData = new HashMap<>();

            redisData.put("last_calc_lat", "Invalid_Data");
            redisData.put("last_calc_lng", "46.63");

            when(hashOperations.entries(eq(routeKey))).thenReturn(redisData);

            RouteCalculationReferenceDTO result = redisTrackingService.getRouteCalculateReference(travelId);

            assertNull(result);
        }
    }

    @Nested
    class getPreviousEta {

        @Test
        @DisplayName("should return stored previous eta and distance with success")
        void shouldReturnStoredPreviousEtaAndDistanceWithSuccess() {
            String expectedDurationRemaining = "20.0";
            String expectedDistanceRemaining = "500.0";
            String expectedTimestamp = String.valueOf(Instant.now().toEpochMilli());

            List<String> fields = Arrays.asList("durationRemaining", "distanceRemaining", "etaTimestamp");

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList(expectedDurationRemaining, expectedDistanceRemaining, expectedTimestamp));

            PreviousStateDTO result = redisTrackingService.getPreviousEta(travelId);

            assertNotNull(result);

            assertEquals(20.0, result.durationRemaining());
            assertEquals(500.0, result.distanceRemaining());

            assertNotNull(result.timeStamp());
        }

        @Test
        @DisplayName("Should return null fields when redis values are null")
        void shouldReturnNullWhenAnyFieldIsNullFromRedis() {
            String expectedDurationRemaining = "20.0";
            String expectedTimestamp = String.valueOf(Instant.now().toEpochMilli());

            List<String> fields = Arrays.asList("durationRemaining", "distanceRemaining", "etaTimestamp");

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList(expectedDurationRemaining, null, expectedTimestamp));

            PreviousStateDTO result = redisTrackingService.getPreviousEta(travelId);

            assertNotNull(result);

            assertEquals(20.0, result.durationRemaining());
            assertNull(result.distanceRemaining());
            assertNotNull(result.timeStamp());
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            PreviousStateDTO result = redisTrackingService.getPreviousEta(null);

            verify(hashOperations, never()).multiGet(any(), any());

            assertNull(result);
        }
    }

    @Nested
    class getLiveLocation {

        @Test
        @DisplayName("should return more recently location and timestamp for front-end")
        void shouldReturnMoreRecentlyLocationAndTimestamp() {
            Map<String, String> expectedMapTracking = new HashMap<>();
            expectedMapTracking.put("current_lat", "-32.932");
            expectedMapTracking.put("current_lng", "-12.402");

            Map<String, String> expectedMapRoute = new HashMap<>();
            expectedMapRoute.put("geometry", "geometry_teste");
            expectedMapRoute.put("distanceRemaining", "392.12");
            expectedMapRoute.put("last_calc_lat", "-32.900");
            expectedMapRoute.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(trackingKey))).thenReturn(expectedMapTracking);
            when(hashOperations.entries(eq(routeKey))).thenReturn(expectedMapRoute);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId);

            verify(hashOperations, times(1)).entries(eq(trackingKey));
            verify(hashOperations, times(1)).entries(eq(routeKey));

            assertNotNull(result);

            assertEquals(-32.932, result.latitude());
            assertEquals(-12.402, result.longitude());
            assertEquals("geometry_teste", result.geometry());
            assertEquals(392.12, result.distance());
            assertEquals(-32.900, result.lastCalcLat());
            assertEquals(-12.400, result.lastCalcLng());
        }

        @Test
        @DisplayName("should return default value when field is null")
        void shouldReturnDefaultValueWhenFieldIsNull() {
            Map<String, String> expectedMapTracking = new HashMap<>();
            expectedMapTracking.put("current_lat", "-32.932");
            expectedMapTracking.put("current_lng", "-12.402");

            Map<String, String> expectedMapRoute = new HashMap<>();
            expectedMapRoute.put("geometry", null);
            expectedMapRoute.put("distanceRemaining", "392.12");
            expectedMapRoute.put("last_calc_lat", null);
            expectedMapRoute.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(trackingKey))).thenReturn(expectedMapTracking);
            when(hashOperations.entries(eq(routeKey))).thenReturn(expectedMapRoute);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId);

            verify(hashOperations, times(1)).entries(eq(trackingKey));
            verify(hashOperations, times(1)).entries(eq(routeKey));

            assertNotNull(result);

            assertEquals(-32.932, result.latitude());
            assertEquals(-12.402, result.longitude());
            assertEquals(392.12, result.distance());
            assertEquals(-12.400, result.lastCalcLng());

            assertNull(result.geometry());
            assertNull(result.lastCalcLat());
        }

        @Test
        @DisplayName("should return silently when field has a invalid value")
        void shouldReturnSilentlyWhenFieldHasInvalidValue() {
            Map<String, String> expectedMapTracking = new HashMap<>();
            expectedMapTracking.put("current_lat", "abc");
            expectedMapTracking.put("current_lng", "-12.402");

            Map<String, String> expectedMapRoute = new HashMap<>();
            expectedMapRoute.put("geometry", null);
            expectedMapRoute.put("distanceRemaining", "add");
            expectedMapRoute.put("last_calc_lat", null);
            expectedMapRoute.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(trackingKey))).thenReturn(expectedMapTracking);
            when(hashOperations.entries(eq(routeKey))).thenReturn(expectedMapRoute);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId);

            verify(hashOperations, times(1)).entries(eq(trackingKey));
            verify(hashOperations, times(1)).entries(eq(routeKey));

            assertNull(result);
        }
    }

    @Nested
    class getLastLocation {

        @Test
        @DisplayName("Should provide last registered location with success")
        void shouldProvideLastRegisteredLocationWhenSuccess() {
            List<String> fields = Arrays.asList("last_ping_lat", "last_ping_lng", "last_ping_timestamp");
            String expectedTs = String.valueOf(Instant.now().toEpochMilli());

            when(hashOperations.multiGet(eq(trackingKey), eq(fields)))
                    .thenReturn(Arrays.asList("-19.732", "-12.634", expectedTs));

            LastLocationDTO result = redisTrackingService.getLastLocation(travelId);

            assertNotNull(result);

            assertEquals(-19.732, result.latitude());
            assertEquals(-12.634, result.longitude());

            assertNotNull(expectedTs);
        }

        @Test
        @DisplayName("should return null when first ping on this method")
        void shouldReturnNullWhenIsFirstPing() {
            List<String> fields = Arrays.asList("last_ping_lat", "last_ping_lng", "last_ping_timestamp");
            String expectedTs = String.valueOf(Instant.now().toEpochMilli());

            when(hashOperations.multiGet(eq(trackingKey), eq(fields)))
                    .thenReturn(Arrays.asList("-19.732", null, expectedTs));

            LastLocationDTO result = redisTrackingService.getLastLocation(travelId);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when any field has a invalid value")
        void shouldReturnNullWhenAnyFieldHasInvalidValue() {
            List<String> fields = Arrays.asList("last_ping_lat", "last_ping_lng", "last_ping_timestamp");
            String expectedTs = String.valueOf(Instant.now().toEpochMilli());

            when(hashOperations.multiGet(eq(trackingKey), eq(fields)))
                    .thenReturn(Arrays.asList("expected_invalid_value", "-12.923", expectedTs));

            LastLocationDTO result = redisTrackingService.getLastLocation(travelId);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when timestamp is null")
        void shouldReturnNullWhenTimestampIsNull() {
            List<String> fields = Arrays.asList("last_ping_lat", "last_ping_lng", "last_ping_timestamp");
            String expectedTs = null;

            when(hashOperations.multiGet(eq(trackingKey), eq(fields)))
                    .thenReturn(Arrays.asList("expected_invalid_value", "-12.923", expectedTs));

            LastLocationDTO result = redisTrackingService.getLastLocation(travelId);

            assertNull(result);
        }
    }

    @Nested
    class getLastMovementState {

        @Test
        @DisplayName("should return the stored field LastMovementState with success")
        void shouldReturnLastMovementStateWithSuccess() {
            List<String> fields = Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt");

            String stateStartedAt = "2026-04-21T10:15:30Z";
            String lastNotification = "2026-04-21T10:20:30Z";
            String lastEta = "2026-04-21T10:22:30Z";

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList("STOPPED", stateStartedAt, lastNotification, lastEta));

            AnalyzeMovementStateDTO result = redisTrackingService.getLastMovementState(travelId);

            assertNotNull(result);

            assertEquals(MovementState.STOPPED, result.movementState());
            assertEquals(Instant.parse(stateStartedAt), result.stateStartedAt());
            assertEquals(Instant.parse(lastNotification), result.lastNotificationSendAt());
        }

        @Test
        @DisplayName("should return silently when cache state is null from redis")
        void shouldReturnSilentlyWhenCacheStateIsNull() {
            List<String> fields = Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt");

            String stateStartedAt = "2026-04-21T10:15:30Z";
            String lastNotification = "2026-04-21T10:20:30Z";
            String lastEta = "2026-04-21T10:22:30Z";

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList(null, stateStartedAt, lastNotification, lastEta));

            AnalyzeMovementStateDTO result = redisTrackingService.getLastMovementState(travelId);

            assertNull(result);
        }

        @Test
        @DisplayName("should return silently when cache state is invalid or corrupted")
        void shouldReturnSilentlyWhenCacheStateIsInvalidOrCorrupted() {
            List<String> fields = Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt");

            String stateStartedAt = "2026-04-21T10:15:30Z";
            String lastNotification = "2026-04-21T10:20:30Z";
            String lastEta = "2026-04-21T10:22:30Z";

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList("", stateStartedAt, lastNotification, lastEta));

            AnalyzeMovementStateDTO result = redisTrackingService.getLastMovementState(travelId);

            assertNull(result);
        }

        @Test
        @DisplayName("should return silently when any field contains invalid data")
        void shouldReturnSilentlyWhenAnyFieldHasInvalidOrCorruptedData() {
            List<String> fields = Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt");

            String stateStartedAt = "2026-04-21T10:15:30Z";
            String lastNotification = "abcd";
            String lastEta = "2026-04-21T10:22:30Z";

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList("STOPPED", stateStartedAt, lastNotification, lastEta));

            AnalyzeMovementStateDTO result = redisTrackingService.getLastMovementState(travelId);

            assertNull(result);
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            AnalyzeMovementStateDTO result = redisTrackingService.getLastMovementState(null);

            assertNull(result);
        }
    }

    @Nested
    class storeTravelMetadata {

        @Test
        @DisplayName("should update remaining ETA, remaining distance and updated status with success")
        void shouldStoreTravelMetadataWithSuccess() {
            Double durationRemaining = 20.0;
            Double distance = 300.0;
            String status = "mocked_status";

            redisTrackingService.storeTravelMetadata(travelId, new RouteDetailsDTO(durationRemaining, distance, null), status);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), mapCaptor.capture());
            Map<String, String> mapCaptorValue = mapCaptor.getValue();

            assertEquals("20.0", mapCaptorValue.get("durationRemaining"));
            assertEquals("300.0", mapCaptorValue.get("distanceRemaining"));
            assertEquals("mocked_status", mapCaptorValue.get("status"));

            assertNotNull(mapCaptorValue.get("metadataUpdatedAt"));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            Double durationRemaining = 20.0;
            Double distance = 300.0;
            String status = "mocked_status";

            redisTrackingService.storeTravelMetadata(null, new RouteDetailsDTO(durationRemaining, distance, null), status);

            verify(hashOperations, never()).putAll(any(), anyMap());
        }
    }

    @Nested
    class keepMemoryBetweenDriverPings {

        @Test
        @DisplayName("should memory between driver pings with success")
        void shouldMemoryBetweenDriverPingsWithSuccess() {
            LiveLocationDTO liveLocationDTO = new LiveLocationDTO(
                    -23.5505,
                    -46.6333,
                    "POINT(-46.6333 -23.5505)",
                    125.5,
                    -23.5490,
                    -46.6345
            );

            redisTrackingService.keepMemoryBetweenDriverPings(travelId, liveLocationDTO);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(trackingKey), mapCaptor.capture());
            Map<String, String> mapCaptorValue = mapCaptor.getValue();

            assertEquals("-23.5505", mapCaptorValue.get("last_ping_lat"));
            assertEquals("-46.6333", mapCaptorValue.get("last_ping_lng"));

            assertNotNull(mapCaptorValue.get("lastPingReceivedAt"));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            LiveLocationDTO liveLocationDTO = new LiveLocationDTO(
                    -23.5505,
                    -46.6333,
                    "POINT(-46.6333 -23.5505)",
                    125.5,
                    -23.5490,
                    -46.6345
            );

            redisTrackingService.keepMemoryBetweenDriverPings(null, liveLocationDTO);

            verify(hashOperations, never()).putAll(any(), anyMap());
        }
    }

    @Nested
    class updateTripEtaState {

        @Test
        @DisplayName("should update trip ETA state of the trip with success")
        void shouldUpdateTripEtaStateWithSuccess() {
            Double distanceRemaining = 250.1;
            Double durationRemaining = 7.01;
            Instant timestamp = Instant.now().minusSeconds(10);

            redisTrackingService.updateTripEtaState(travelId, distanceRemaining, durationRemaining, timestamp);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> captureMapValue = captorMap.getValue();

            assertEquals("250.1", captureMapValue.get("distanceRemaining"));
            assertEquals("7.01", captureMapValue.get("durationRemaining"));

            assertNotNull(captureMapValue.get("etaLastUpdatedAt"));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            Double distanceRemaining = 250.1;
            Double durationRemaining = 7.01;
            Instant timestamp = Instant.now().minusSeconds(10);

            redisTrackingService.updateTripEtaState(null, distanceRemaining, durationRemaining, timestamp);

            verify(hashOperations, never()).putAll(any(), anyMap());
        }
    }

    @Nested
    class saveAnalyzedMovementState {

        @Test
        @DisplayName("should save actually state if stored movement state is null, and should update all fields")
        void shouldSaveActuallyStateIfStoredMovementStateIsNull() {
            List<String> fields = Arrays.asList("movementState", "lastNotificationSendAt", "lastEtaNotificationAt");

            AnalyzeMovementStateDTO dto = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.parse("2026-04-23T18:50:00Z"),
                    Instant.parse("2026-04-23T18:45:00Z"),
                    Instant.parse("2026-04-23T21:50:00Z")
            );

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList(null, String.valueOf(dto.lastNotificationSendAt()), String.valueOf(dto.lastEtaNotificationAt())));

            redisTrackingService.saveAnalyzedMovementState(travelId, dto);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> capturedValues = captorMap.getValue();

            assertEquals("STOPPED", capturedValues.get("movementState"));

            assertEquals("2026-04-23T18:45:00Z", capturedValues.get("lastNotificationSendAt"));
            assertEquals("2026-04-23T21:50:00Z", capturedValues.get("lastEtaNotificationAt"));
        }

        @Test
        @DisplayName("should update redis if movementState are different, and should update all fields")
        void shouldUpdateRedisIfMovementStateAreDifferent() {
            List<String> fields = Arrays.asList("movementState", "lastNotificationSendAt", "lastEtaNotificationAt");

            AnalyzeMovementStateDTO dto = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.parse("2026-04-23T18:50:00Z"),
                    Instant.parse("2026-04-23T18:45:00Z"),
                    Instant.parse("2026-04-23T21:50:00Z")
            );

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList("NORMAL", String.valueOf(dto.lastNotificationSendAt()), String.valueOf(dto.lastEtaNotificationAt())));

            redisTrackingService.saveAnalyzedMovementState(travelId, dto);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> capturedValues = captorMap.getValue();

            assertEquals("STOPPED", capturedValues.get("movementState"));

            assertEquals("2026-04-23T18:45:00Z", capturedValues.get("lastNotificationSendAt"));
            assertEquals("2026-04-23T21:50:00Z", capturedValues.get("lastEtaNotificationAt"));
        }

        @Test
        @DisplayName("should update only movement state from redis when both movementState are equals")
        void shouldUpdateOnlyMovementStateFromRedisWhenBothMovementStateAreEquals() {
            List<String> fields = Arrays.asList("movementState", "lastNotificationSendAt", "lastEtaNotificationAt");

            AnalyzeMovementStateDTO dto = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.parse("2026-04-23T18:50:00Z"),
                    Instant.parse("2026-04-23T18:45:00Z"),
                    Instant.parse("2026-04-23T21:50:00Z")
            );

            when(hashOperations.multiGet(eq(routeKey), eq(fields)))
                    .thenReturn(Arrays.asList("STOPPED", String.valueOf(dto.lastNotificationSendAt()), String.valueOf(dto.lastEtaNotificationAt())));

            redisTrackingService.saveAnalyzedMovementState(travelId, dto);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(routeKey), captorMap.capture());
            Map<String, String> capturedValues = captorMap.getValue();

            assertEquals("STOPPED", capturedValues.get("movementState"));
        }

        @Test
        @DisplayName("should return silently when analyzeMovement is null, because is first ping to the method")
        void shouldReturnSilentlyWhenAnalyzeMovementIsNull() {
            UUID travelId = UUID.randomUUID();

            redisTrackingService.saveAnalyzedMovementState(travelId, null);

            verifyNoInteractions(hashOperations);
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            AnalyzeMovementStateDTO dto = new AnalyzeMovementStateDTO(
                    MovementState.STOPPED,
                    Instant.parse("2026-04-23T18:50:00Z"),
                    Instant.parse("2026-04-23T18:45:00Z"),
                    Instant.parse("2026-04-23T21:50:00Z")
            );

            redisTrackingService.saveAnalyzedMovementState(null, dto);

            verifyNoInteractions(hashOperations);
        }

    }

    @Nested
    class markNotificationAsSent {
        
        @Test
        @DisplayName("should mark notification as sent with success")
        void shouldMarkNotificationAsSentWithSuccess() {
            redisTrackingService.markNotificationAsSent(travelId);

            verify(hashOperations, times(1)).put(eq(routeKey), eq("lastNotificationSendAt"), anyString());
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldReturnSilentlyWhenTravelIdIsNull() {
            redisTrackingService.markNotificationAsSent(null);

            verify(hashOperations, never()).put(any(), anyString(), anyString());
        }
    }

    @Nested
    class addActiveTravel {

        @Test
        @DisplayName("should add active id travel in redis set with success")
        void shouldAddActiveIdTravelInRedisSetWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String setKey = "ACTIVE_TRAVELS_KEY";

            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            redisTrackingService.addActiveTravel(travelId);

            verify(setOperations, times(1)).add(eq(setKey), eq(travelId.toString()));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldRetornSilentlyWhenTravelIdIsNull() {
            redisTrackingService.addActiveTravel(null);

            verify(setOperations, never()).add(any(), anyString());
        }
    }

    @Nested
    class removeUnactiveTravel {

        @Test
        @DisplayName("should remove unactive travelId in redis with success")
        void shouldRemoveUnactiveTravelIdInRedisWithSuccess() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            redisTrackingService.removeUnactiveTravel(travelId);

            verify(setOperations, times(1)).remove(eq(activeTravelKey), eq(travelId.toString()));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldRetornSilentlyWhenTravelIdIsNull() {
            redisTrackingService.removeUnactiveTravel(null);

            verify(setOperations, never()).remove(any(), anyString());
        }
    }

    @Nested
    class getAllActiveTravelsId {

        @Test
        @DisplayName("should return all active travels ids in redis set")
        void shouldReturnAllIdsOfActiveTravelsInRedisSet() {
            String setKey = "ACTIVE_TRAVELS_KEY";

            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            Set<String> result = redisTrackingService.getAllActiveTravelsId();

            verify(setOperations, times(1)).members(eq(setKey));

            assertNotNull(result);
        }
    }

    @Nested
    class getLastPingTimestamp {

        @Test
        @DisplayName("should return the last moment recorded by gps with success")
        void shouldReturnTheLastMomentRecordedByGpsWithSuccess() {
            String timestamp = String.valueOf(Instant.parse("2026-04-23T18:50:00Z").toEpochMilli());

            when(hashOperations.get(eq(trackingKey), eq("lastPingReceivedAt"))).thenReturn(timestamp);

            Long result = redisTrackingService.getLastPingTimestamp(travelId);

            assertNotNull(result);

            verify(hashOperations, times(1)).get(eq(trackingKey), eq("lastPingReceivedAt"));
        }

        @Test
        @DisplayName("should return null if the stored value from redis is null")
        void shouldReturnNullIfTheStoredValueIsNull() {
            when(hashOperations.get(eq(trackingKey), eq("lastPingReceivedAt"))).thenReturn(null);

            Long result = redisTrackingService.getLastPingTimestamp(travelId);

            assertNull(result);

            verify(hashOperations, times(1)).get(eq(trackingKey), eq("lastPingReceivedAt"));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldRetornSilentlyWhenTravelIdIsNull() {
            redisTrackingService.getLastPingTimestamp(null);

            verify(hashOperations, never()).get(anyDouble(), anyString());
        }
    }

    @Nested
    class clearTravelLocationCache {

        @Test
        @DisplayName("should clear redis cache data from travel")
        void shouldClearRedisCacheDataFromTravel() {
            List<String> expectedKeys = List.of(routeKey, trackingKey);

            when(redisTemplate.delete(eq(expectedKeys))).thenReturn(2L);
            when(redisTemplate.opsForSet()).thenReturn(setOperations);

            redisTrackingService.clearTravelLocationCache(travelId);

            verify(redisTemplate, times(1)).delete(eq(expectedKeys));
            verify(setOperations, times(1)).remove(eq(activeTravelKey), eq(travelId.toString()));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldRetornSilentlyWhenTravelIdIsNull() {
            redisTrackingService.clearTravelLocationCache(null);

            verify(setOperations, never()).remove(anyString(), anyDouble());
        }
    }

    @Nested
    class saveHistoryPingLocation {

        @Test
        @DisplayName("should save the last history ping from the trip")
        void shouldSaveTheLastHistoryPingFromTheTrip() {
            Instant lastPing = Instant.now();

            redisTrackingService.saveHistoryPingLocation(travelId, lastPing);

            verify(hashOperations, times(1)).put(eq(trackingKey), eq("last_ping_history"), eq(lastPing.toString()));
        }

        @Test
        @DisplayName("should return silently when travelId is null")
        void shouldRetornSilentlyWhenTravelIdIsNull() {
            Instant lastPing = Instant.now();

            redisTrackingService.saveHistoryPingLocation(null, lastPing);

            verify(hashOperations, never()).put(anyDouble(), anyString(), any());
        }

    }

    @Nested
    class isLocationUpdateAllowed {

        @Test
        @DisplayName("should return true if the last ping is saved less than allowed seconds")
        void shouldReturnTrueIfTheLastPingIsSavedLessThanAllowedSeconds() {
            Instant fifteenSecondsAgo = Instant.now().minusSeconds(15);
            String lastPingString = fifteenSecondsAgo.toString();

            when(hashOperations.get(eq(trackingKey), eq("last_ping_history"))).thenReturn(lastPingString);

            boolean result = redisTrackingService.isLocationUpdateAllowed(travelId);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false if the last ping was less than 10s ago")
        void shouldReturnFalseIfLastPingIsRecent() {
            Instant fifteenSecondsAgo = Instant.now().minusSeconds(2);
            String lastPingString = fifteenSecondsAgo.toString();

            when(hashOperations.get(eq(trackingKey), eq("last_ping_history"))).thenReturn(lastPingString);

            boolean result = redisTrackingService.isLocationUpdateAllowed(travelId);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return true if first ping")
        void shouldReturnTrueIfFirstPing() {
            when(hashOperations.get(eq(trackingKey), eq("last_ping_history"))).thenReturn(null);

            boolean result = redisTrackingService.isLocationUpdateAllowed(travelId);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false if travelId is null")
        void shouldReturnFalseIfTravelIdIsNull() {
            boolean result = redisTrackingService.isLocationUpdateAllowed(null);

            assertFalse(result);

            verify(hashOperations, never()).get(any(), any());
        }
    }

    @Nested
    class markStudentAsAway {
        UUID studentId;
        String studentTravelKey;

        final String STUDENT_TRAVEL_KEY_PREFIX = "student:travel:";
        @BeforeEach
        void setUp() {
            studentId = UUID.randomUUID();
            studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + studentId + ":" + travelId;

        }

        @Test
        @DisplayName("Deve registrar estudantes afastados com sucesso convertendo IDs e timestamps para String")
        void shouldRegisterAwayStudentsSuccessfully() {
            UUID travelId = UUID.randomUUID();
            UUID studentId2 = UUID.randomUUID();
            long timestamp = Instant.now().toEpochMilli();

            Map<UUID, Long> studentsToMarkAway = new HashMap<>();
            studentsToMarkAway.put(studentId, timestamp);
            studentsToMarkAway.put(studentId2, timestamp);

            redisTrackingService.markStudentAsAway(travelId, studentsToMarkAway);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            String expectedKey = "travel:away_students:" + travelId;
            verify(hashOperations, times(1)).putAll(eq(expectedKey), mapCaptor.capture());

            Map<String, String> capturedMap = mapCaptor.getValue();
            assertNotNull(capturedMap);
            assertEquals(2, capturedMap.size());
            assertEquals(String.valueOf(timestamp), capturedMap.get(studentId.toString()));
            assertEquals(String.valueOf(timestamp), capturedMap.get(studentId2.toString()));
        }

        @Test
        @DisplayName("Deve abortar o registro de estudantes quando os parâmetros forem inválidos ou vazios")
        void shouldNotRegisterAwayStudentsWhenParametersAreInvalidOrEmpty() {
            UUID travelId = UUID.randomUUID();
            Map<UUID, Long> emptyStudentsMap = Collections.emptyMap();
            Map<UUID, Long> validStudentsMap = Map.of(UUID.randomUUID(), Instant.now().toEpochMilli());

            redisTrackingService.markStudentAsAway(null, validStudentsMap);

            redisTrackingService.markStudentAsAway(travelId, emptyStudentsMap);

            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }
    }

    @Nested
    class getStudentAwayTimestamp {
        UUID studentId;
        String studentTravelKey;

        final String STUDENT_TRAVEL_KEY_PREFIX = "student:travel:";
        @BeforeEach
        void setUp() {
            studentId = UUID.randomUUID();
            studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + studentId + ":" + travelId;

        }

        @Test
        @DisplayName("Deve recuperar os timestamps de afastamento dos estudantes com sucesso")
        void shouldGetStudentAwayTimestampSuccessfully() {
            UUID travelId = UUID.randomUUID();
            UUID studentId1 = UUID.randomUUID();
            UUID studentId2 = UUID.randomUUID();
            long timestamp = Instant.now().toEpochMilli();

            String expectedKey = "travel:away_students:" + travelId;
            Map<String, String> redisData = new HashMap<>();
            redisData.put(studentId1.toString(), String.valueOf(timestamp));
            redisData.put(studentId2.toString(), String.valueOf(timestamp));

            when(hashOperations.entries(expectedKey)).thenReturn(redisData);

            Map<UUID, Long> result = redisTrackingService.getStudentAwayTimestamp(travelId);

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(timestamp, result.get(studentId1));
            assertEquals(timestamp, result.get(studentId2));
        }

        @Test
        @DisplayName("Deve retornar um mapa vazio quando não houver registros de afastamento para a viagem no Redis")
        void shouldReturnEmptyMapWhenNoStudentAwayRecordsExist() {
            UUID travelId = UUID.randomUUID();
            String expectedKey = "travel:away_students:" + travelId;

            when(hashOperations.entries(expectedKey)).thenReturn(Collections.emptyMap());

            Map<UUID, Long> result = redisTrackingService.getStudentAwayTimestamp(travelId);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEquals(Collections.emptyMap(), result);
        }

        @Test
        @DisplayName("Deve retornar null imediatamente sem consultar o Redis quando o ID da viagem for nulo")
        void shouldReturnNullWhenTravelIdIsNull() {
            Map<UUID, Long> result = redisTrackingService.getStudentAwayTimestamp(null);

            assertNull(result);
            verify(hashOperations, never()).entries(anyString());
        }
    }

    @Nested
    class clearStudentAwayState {
        UUID studentId;
        String studentTravelKey;

        final String STUDENT_TRAVEL_KEY_PREFIX = "student:travel:";
        @BeforeEach
        void setUp() {
            studentId = UUID.randomUUID();
            studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + studentId + ":" + travelId;
        }

        @Test
        @DisplayName("Deve remover do Redis apenas os IDs dos estudantes presentes")
        void shouldClearStudentAwayStateSuccessfully() {
            UUID travelId = UUID.randomUUID();
            UUID studentId1 = UUID.randomUUID();
            UUID studentId2 = UUID.randomUUID();
            Set<UUID> studentIds = Set.of(studentId1, studentId2);

            String expectedKey = "travel:away_students:" + travelId;

            redisTrackingService.clearStudentAwayState(travelId, studentIds);

            ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
            verify(hashOperations, times(1)).delete(eq(expectedKey), captor.capture());

            String[] capturedIds = captor.getValue();
            assertNotNull(capturedIds);
            assertEquals(2, capturedIds.length);
            List<String> capturedList = Arrays.asList(capturedIds);
            assertTrue(capturedList.contains(studentId1.toString()));
            assertTrue(capturedList.contains(studentId2.toString()));
        }

        @Test
        @DisplayName("Deve abortar a limpeza do estado quando os parâmetros forem inválidos ou vazios")
        void shouldNotClearStudentAwayStateWhenParametersAreInvalidOrEmpty() {
            UUID travelId = UUID.randomUUID();
            Set<UUID> emptyStudentIds = Collections.emptySet();
            Set<UUID> validStudentIds = Set.of(UUID.randomUUID());

            redisTrackingService.clearStudentAwayState(null, validStudentIds);
            redisTrackingService.clearStudentAwayState(travelId, emptyStudentIds);

            verify(hashOperations, never()).delete(anyString(), any(Object[].class));
        }
    }

    @Nested
    class tryAcquireStudentAwayStateLock {

        @Test
        @DisplayName("Deve adquirir o lock de estado de estudantes afastados com sucesso")
        void shouldAcquireStudentAwayStateLockSuccessfully() {
            UUID travelId = UUID.randomUUID();
            String expectedLockKey = "travel:student-away-lock:" + travelId;

            ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(expectedLockKey), eq("locked"), eq(Duration.ofSeconds(30))))
                    .thenReturn(Boolean.TRUE);

            boolean acquired = redisTrackingService.tryAcquireStudentAwayStateLock(travelId);

            assertTrue(acquired);
            verify(valueOperations, times(1))
                    .setIfAbsent(eq(expectedLockKey), eq("locked"), eq(Duration.ofSeconds(30)));
        }

        @Test
        @DisplayName("Deve retornar falso ao tentar adquirir o lock quando ele já estiver ativo (bloqueado)")
        void shouldReturnFalseWhenStudentAwayStateLockIsAlreadyActive() {
            UUID travelId = UUID.randomUUID();
            String expectedLockKey = "travel:student-away-lock:" + travelId;

            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(expectedLockKey), eq("locked"), eq(Duration.ofSeconds(30))))
                    .thenReturn(Boolean.FALSE);

            boolean acquired = redisTrackingService.tryAcquireStudentAwayStateLock(travelId);

            assertFalse(acquired);
            verify(valueOperations, times(1))
                    .setIfAbsent(eq(expectedLockKey), eq("locked"), eq(Duration.ofSeconds(30)));
        }
    }

    @Nested
    class releaseStudentAwayStateLock {
        @Test
        @DisplayName("Cenário 4.3: Deve liberar o lock de estado de estudantes afastados com sucesso removendo a chave do Redis")
        void shouldReleaseStudentAwayStateLockSuccessfully() {
            UUID travelId = UUID.randomUUID();
            String expectedLockKey = "travel:student-away-lock:" + travelId;

            redisTrackingService.releaseStudentAwayStateLock(travelId);

            verify(redisTemplate, times(1)).delete(eq(expectedLockKey));
        }
    }
}
