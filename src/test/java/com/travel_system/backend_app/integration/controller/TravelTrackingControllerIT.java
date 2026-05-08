package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.CityRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.PermissionsRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.GpsDataIngestorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelTrackingControllerIT extends IntegrationTestBase {

    private final Logger logger = LoggerFactory.getLogger(TravelTrackingControllerIT.class);

    @Autowired
    private TravelRepository travelRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private PermissionsRepository permissionsRepository;
    @Autowired
    private GpsDataIngestorService gpsDataIngestorService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        doReturn(new RouteDetailsDTO(1200.0, 5000.0, "~shnC~_rcL_@v@m@p@y@r@"))
                .when(mapboxAPIService).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // limpa a cada teste
        travelRepository.deleteAll();
        cityRepository.deleteAll();
        driverRepository.deleteAll();
        permissionsRepository.deleteAll();
    }

    @Nested
    class markDriverCheckpoint {
        UUID cityId;
        UUID travelId;
        VehicleLocationRequestDTO requestDTO;
        Travel travel;

        @BeforeEach
        void setUp() {
            Permissions permission = new Permissions("ROLE_DRIVER");
            permissionsRepository.save(permission);

            City city = new City(null, "Salvador", CitySize.TOWN, true);
            cityRepository.save(city);
            cityId = city.getId();

            Driver driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>());
            driver.setPermissions(List.of(permission));
            driverRepository.save(driver);

             travel = new Travel(
                    null, city, TravelStatus.TRAVELLING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);
            travel.setStudentTravels(new HashSet<>());
            travelId = travel.getId();

            requestDTO = new VehicleLocationRequestDTO(travelId, -12.9750, -38.5020, 60.0, 180.0);
        }

        @Nested
        class successTestScenarios {
            @Test
            @DisplayName("should verify the response status (expect 200), + redis behavior's with lat/lng/timestamp + distance null + geometry null")
            void shouldMarkDriverCheckpointOnFirstPingWithSuccess() throws Exception {
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(null);

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                // await para esperar o processamento assíncrono, porém ele falhará silenciosamente por conta da mapboxapi estar retornando null
                await().atMost(2, SECONDS).untilAsserted(() -> {
                    String savedLat = Objects.requireNonNull(redisTemplate.opsForHash().get("travelId:" + travelId, "last_calc_lat")).toString();
                    assertEquals(String.valueOf(requestDTO.latitude()), savedLat);
                });

                String key = "travelId:" + travelId;
                HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();

                String savedLat = hashOperations.get(key, "last_calc_lat");
                String savedLng = hashOperations.get(key, "last_calc_lng");
                String savedTimestamp = hashOperations.get(key, "timestamp");
                String savedDistance = hashOperations.get(key, "distanceRemaining");
                String savedGeometry = hashOperations.get(key, "geometry");

                assertEquals(String.valueOf(requestDTO.latitude()), savedLat);
                assertEquals(String.valueOf(requestDTO.longitude()), savedLng);

                assertNotNull(savedTimestamp);

                assertNull(savedDistance);
                assertNull(savedGeometry);
            }

            @Test
            @DisplayName("should process full location flow async after first ping (offRoute path)")
            void shouldProcessLocationAfterFirstPingAsync() throws Exception {
                // dispara o checkpoint que publica o evento async
                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                // aguardar o processamento com awailitily
                String key = "travelId:" + travelId;
                await().atMost(3, SECONDS).untilAsserted(() -> {
                    String distanceRemaining = (String) redisTemplate.opsForHash().get(key, "distanceRemaining");
                    assertNotNull(distanceRemaining);
                });

                // verificando o status final no redis
                HashOperations<String, String, String> redisHash = redisTemplate.opsForHash();

                String savedDistanceRemaining = redisHash.get(key, "distanceRemaining");
                String savedDurationRemaining = redisHash.get(key, "durationRemaining");
                String savedStatus = redisHash.get(key, "status");

                assertEquals("5000.0", savedDistanceRemaining);
                assertEquals("1200.0", savedDurationRemaining);
                assertEquals("TRAVELLING", savedStatus);
            }
        }

        @Nested
        class failureTestScenarios {

            @ParameterizedTest
            @DisplayName("should return 400 bad request when request body is null or invalid")
            @MethodSource("nullVehicleLocationDtoProvider")
            void shouldReturnBadRequestWhenRequestBodyIsInvalid(VehicleLocationRequestDTO vehicleLocationRequestDTO) throws Exception {
                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehicleLocationRequestDTO)))
                        .andExpect(status().isBadRequest());
            }

            public static Stream<Arguments> nullVehicleLocationDtoProvider() {
                return Stream.of(
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.5020, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, null, 60.0, 180.0)),
                        Arguments.of((VehicleLocationRequestDTO) null)
                );
            }

            @Test
            @DisplayName("should return not found status code when travel not found from database")
            void shouldReturnNotFoundCodeWhenTravelNotFound() throws Exception {
                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isNotFound());
            }

            @Test
            @DisplayName("should return conflict status code when travel is not travelling")
            void shouldReturnConflictCodeWhenTravelIsNotTravelling() throws Exception {
                travel.setTravelStatus(TravelStatus.PENDING);
                travelRepository.save(travel);

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isConflict());
            }

            @Test
            @DisplayName("should not store data in redis when exception occurs before")
            void shouldNotStoreDataInRedisWhenExceptionOccursBefore() throws Exception {
                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, null)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isNotFound());

                String key = "travelId:" + travelId;

                assertFalse(redisTemplate.hasKey(key));
            }

            @Test
            void shouldNeverPublishAsyncEventWhenAnyValidationFails() throws Exception {
                travel.setTravelStatus(TravelStatus.PENDING);
                travelRepository.save(travel);

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isConflict());

                verify(rabbitTemplate, never()).convertAndSend(any());
            }

            @Test
            @DisplayName("should preserve previous distance and geometry data when updating live location")
            void shouldPreservePreviousDistanceAndGeometryWhenUpdatingLiveLocation() throws Exception {
                String key = "travelId:" + travelId;

                // estado anterior no redis
                redisTemplate.opsForHash().put(key, "distance", "400.0");
                redisTemplate.opsForHash().put(key, "geometry", "encoded_polyline");

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                HashOperations<String, String, String> redisHash = redisTemplate.opsForHash();

                // valida se preservou
                assertEquals("400.0", redisHash.get(key, "distance"));
                assertEquals("encoded_polyline", redisHash.get(key, "geometry"));

                // valida se a position foi atualizada
                assertEquals(String.valueOf(requestDTO.latitude()), redisHash.get(key, "last_calc_lat"));
                assertEquals(String.valueOf(requestDTO.longitude()), redisHash.get(key, "last_calc_lng"));
            }
        }


    }
}