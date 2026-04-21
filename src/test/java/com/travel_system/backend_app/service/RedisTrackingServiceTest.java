package com.travel_system.backend_app.service;

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


    }
}
