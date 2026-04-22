package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTrackingServiceTest {
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

    private RedisTrackingService redisTrackingService;

    @Mock
    private RouteCalculationService routeCalculationService;

    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private HashOperations hashOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        redisTrackingService = new RedisTrackingService(routeCalculationService, redisTemplate);
    }

    @Nested
    class storeLiveLocation {

        @Test
        @DisplayName("should stored more recently live location with success")
        void shouldStoredMoreRecentlyLiveLocationWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String latitude = "-32.932";
            String longitude = "-12.402";
            Double distance = 392.12;
            String geometry = "geometry_teste";

            String key = "travelId:" + travelId;

            List<String> fields = Arrays.asList("last_calc_lat", "last_calc_lng", "accumulatedDistance");
            List<String> values = Arrays.asList("-32.932", "-12.402", null);

            Map<String, String> oldDataMock = new HashMap<>();

            when(hashOperations.multiGet(eq(key), eq(fields))).thenReturn(values);
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(392.12);

            redisTrackingService.storeLiveLocation(travelId.toString(), latitude, longitude, distance, geometry);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(anyString(), captorMap.capture());
            Map<String, String> savedMap = captorMap.getValue();

            assertEquals(latitude, savedMap.get("last_calc_lat"));
            assertEquals(longitude, savedMap.get("last_calc_lng"));

            assertEquals("392.12", savedMap.get("accumulatedDistance"));
        }

        @Test
        @DisplayName("should initialize accumulated distance on first location with success")
        void shouldInitializeAccumulatedDistanceOnFirstLocationWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String latitude = "-32.932";
            String longitude = "-12.402";
            Double distance = 302.12;
            String geometry = "geometry_teste";

            String key = "travelId:" + travelId;

            List<String> fields = Arrays.asList("last_calc_lat", "last_calc_lng", "accumulatedDistance");
            List<String> values = Arrays.asList(null, null, null);

            when(hashOperations.multiGet(eq(key), eq(fields))).thenReturn(values);
//            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
//                    .thenReturn(392.12);

            redisTrackingService.storeLiveLocation(travelId.toString(), latitude, longitude, distance, geometry);

            ArgumentCaptor<Map<String, String>> captorMap = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(anyString(), captorMap.capture());
            Map<String, String> savedMap = captorMap.getValue();

            System.out.println("savedMap" + savedMap);

            assertEquals(latitude, savedMap.get("last_calc_lat"));
            assertEquals(longitude, savedMap.get("last_calc_lng"));

            assertEquals("302.12", savedMap.get("accumulatedDistance"));
        }

        @Test
        @DisplayName("should continue processing when input parameters are null")
        void shouldContinueProcessingWhenInputParametersAreNull() {
            UUID travelId = UUID.randomUUID();

            redisTrackingService.storeLiveLocation(travelId.toString(), null, null, null, null);

            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }

        @Test
        @DisplayName("should continue processing when stored coordinates are invalid")
        void shouldContinueProcessingWhenStoredCoordinatesAreInvalid() {
            UUID travelId = UUID.randomUUID();
            String latitude = "-32.932";
            String longitude = "-12.402";
            Double distance = 392.12;
            String geometry = "geometry_teste";

            String key = "travelId:" + travelId;

            List<String> fields = Arrays.asList("last_calc_lat", "last_calc_lng", "accumulatedDistance");
            List<String> values = Arrays.asList("abc", "-12.402", null);

            when(hashOperations.multiGet(eq(key), eq(fields))).thenReturn(values);

            redisTrackingService.storeLiveLocation(travelId.toString(), latitude, longitude, distance, geometry);

            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }
    }

    @Nested
    class getAccumulatedDistance {

        @Test
        @DisplayName("should return accumulated distance stored from redis with success")
        void shouldReturnAccumulatedDistanceWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            String expectedAccumulatedDist = "50.0";

            when(hashOperations.get(eq(key), eq("accumulatedDistance"))).thenReturn(expectedAccumulatedDist);

            String result = redisTrackingService.getAccumulatedDistance(travelId);

            verify(hashOperations, times(1)).get(any(), any());

            assertEquals("50.0", result);
        }

        @Test
        @DisplayName("should return zero when accumulated distance are null from redis")
        void shouldReturnZeroWhenAccumulatedDistanceAreNull() {
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            when(hashOperations.get(eq(key), eq("accumulatedDistance"))).thenReturn(null);

            String result = redisTrackingService.getAccumulatedDistance(travelId);

            verify(hashOperations, times(1)).get(any(), any());

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
    class getPreviousEta {

        @Test
        @DisplayName("should return stored previous eta and distance with success")
        void shouldReturnStoredPreviousEtaAndDistanceWithSuccess() {
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            String expectedDurationRemaining = "20.0";
            String expectedDistanceRemaining = "500.0";
            String expectedTimestamp = String.valueOf(Instant.now().toEpochMilli());

            List<String> fields = Arrays.asList("durationRemaining", "distanceRemaining", "timestamp");

            when(hashOperations.multiGet(eq(key), eq(fields)))
                    .thenReturn(Arrays.asList(expectedDurationRemaining, expectedDistanceRemaining, expectedTimestamp));

            PreviousStateDTO result = redisTrackingService.getPreviousEta(travelId.toString());

            assertNotNull(result);

            assertEquals(20.0, result.durationRemaining());
            assertEquals(500.0, result.distanceRemaining());

            assertNotNull(result.timeStamp());
        }

        @Test
        @DisplayName("Should return null fields when redis values are null")
        void shouldReturnNullWhenAnyFieldIsNullFromRedis() {
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            String expectedDurationRemaining = "20.0";
            String expectedTimestamp = String.valueOf(Instant.now().toEpochMilli());

            List<String> fields = Arrays.asList("durationRemaining", "distanceRemaining", "timestamp");

            when(hashOperations.multiGet(eq(key), eq(fields)))
                    .thenReturn(Arrays.asList(expectedDurationRemaining, null, expectedTimestamp));

            PreviousStateDTO result = redisTrackingService.getPreviousEta(travelId.toString());

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
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("lat", "-32.932");
            expectedMap.put("lng", "-12.402");
            expectedMap.put("geometry", "geometry_teste");
            expectedMap.put("distance", "392.12");
            expectedMap.put("last_calc_lat", "-32.900");
            expectedMap.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(key))).thenReturn(expectedMap);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId.toString());

            verify(hashOperations, times(1)).entries(eq(key));

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
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("lat", "-32.932");
            expectedMap.put("lng", "-12.402");
            expectedMap.put("geometry", null);
            expectedMap.put("distance", "392.12");
            expectedMap.put("last_calc_lat", null);
            expectedMap.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(key))).thenReturn(expectedMap);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId.toString());

            verify(hashOperations, times(1)).entries(eq(key));

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
            UUID travelId = UUID.randomUUID();
            String key = "travelId:" + travelId;

            Map<String, String> expectedMap = new HashMap<>();
            expectedMap.put("lat", "-32.932");
            expectedMap.put("lng", "abc");
            expectedMap.put("geometry", null);
            expectedMap.put("distance", "392.12");
            expectedMap.put("last_calc_lat", null);
            expectedMap.put("last_calc_lng", "-12.400");

            when(hashOperations.entries(eq(key))).thenReturn(expectedMap);

            LiveLocationDTO result = redisTrackingService.getLiveLocation(travelId.toString());

            verify(hashOperations, times(1)).entries(eq(key));

            assertNull(result);
        }
    }
}
