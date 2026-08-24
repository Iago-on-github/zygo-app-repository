package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelCacheServiceTest {

    @InjectMocks
    private TravelCacheService travelCacheService;

    @Mock
    private TravelRepository travelRepository;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private HashOperations hashOperations;

    private final String TRAVEL_STATIC_CACHE = "trip:static:";

    Travel travel;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        travelCacheService = new TravelCacheService(travelRepository, redisTemplate);

        travel = new Travel(UUID.randomUUID(), TravelStatus.PENDING, new Driver(), Instant.now(), Instant.now(), TravelPeriod.EVENING, Instant.now().plusSeconds(3600), "u{~vFcwpt@_@_C", 3600.0, 15.5, -23.55052, -46.633308, -23.561414, -46.655881, "São Paulo", new Customer(), null);

    }

    @Nested
    class invalidateTravelStaticCache {

        @Test
        @DisplayName("Deve invalidar todo o cache armazenado com base na KEY com sucesso")
        void shouldInvalidateCacheWithSuccess() {
            String key = TRAVEL_STATIC_CACHE + travel.getId();

            travelCacheService.invalidateTravelStaticCache(travel.getId());

            verify(redisTemplate, times(1)).delete(key);
        }
    }

    @Nested
    class getOrLoadTravelStaticCache {

        @Test
        @DisplayName("Deve retornar o cache quando soliciatado com sucesso")
        void shouldReturnDataFromRedisWhenCacheHitOccurs() {
            String key = TRAVEL_STATIC_CACHE + travel.getId();

            Map<String, String> redisMockData = Map.of(
                    "travelStatus", travel.getTravelStatus().toString(),
                    "polyline", travel.getPolylineRoute(),
                    "finalLatitude", travel.getFinalLatitude().toString(),
                    "finalLongitude", travel.getFinalLongitude().toString(),
                    "distance", travel.getDistance().toString(),
                    "duration", travel.getDuration().toString()
            );

            when(hashOperations.entries(key)).thenReturn(redisMockData);

            TravelCacheDTO result = travelCacheService.getOrLoadTravelStaticCache(travel.getId());

            assertNotNull(result);
            assertEquals(travel.getTravelStatus(), result.travelStatus());
            assertEquals(travel.getPolylineRoute(), result.polylineRoute());
            assertEquals(travel.getFinalLatitude(), result.finalLatitude());
            assertEquals(travel.getFinalLongitude(), result.finalLongitude());
            assertEquals(travel.getDistance(), result.distance());
            assertEquals(travel.getDuration(), result.duration());

            verifyNoInteractions(travelRepository);
        }

        @Test
        @DisplayName("Deve realizar busca por novos dados no banco pois não há cache armazenado")
        void shouldLoadDataFromDatabaseAndCacheItWhenCacheMissOccurs() {
            String expectedKey = "trip:static:" + travel.getId();
            
            when(hashOperations.entries(expectedKey)).thenReturn(Collections.emptyMap());
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            TravelCacheDTO result = travelCacheService.getOrLoadTravelStaticCache(travel.getId());

            assertNotNull(result);
            assertEquals(travel.getId(), result.travelId());
            assertEquals(travel.getTravelStatus(), result.travelStatus());
            assertEquals(travel.getPolylineRoute(), result.polylineRoute());
            assertEquals(travel.getFinalLatitude(), result.finalLatitude());
            assertEquals(travel.getFinalLongitude(), result.finalLongitude());
            assertEquals(travel.getDistance(), result.distance());
            assertEquals(travel.getDuration(), result.duration());

            verify(hashOperations, times(1)).entries(expectedKey);
            verify(travelRepository, times(1)).findById(travel.getId());

            verify(hashOperations, times(1)).putAll(eq(expectedKey), argThat(map ->
                    map.containsKey("travelStatus") &&
                            map.containsKey("polyline") &&
                            map.containsKey("finalLatitude") &&
                            map.containsKey("finalLongitude") &&
                            map.containsKey("distance") &&
                            map.containsKey("duration")
            ));
        }

        @Test
        @DisplayName("Deve lancar EntityNotFoundException quando houver cache miss e a viagem nao existir no banco")
        void shouldThrowEntityNotFoundExceptionWhenCacheMissAndTravelDoesNotExistInDatabase() {
            String expectedKey = "trip:static:" + travel.getId();

            when(hashOperations.entries(expectedKey)).thenReturn(Collections.emptyMap());
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> travelCacheService.getOrLoadTravelStaticCache(travel.getId()));

            verify(hashOperations, times(1)).entries(expectedKey);
            verify(travelRepository, times(1)).findById(travel.getId());
            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }
    }
}