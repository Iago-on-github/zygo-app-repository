package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.GpsDataIngestorService;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.RouteCalculationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private StudentTravelRepository studentTravelRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TravelLocationHistoryRepository travelLocationHistoryRepository;

    @MockitoBean
    private RouteCalculationService routeCalculationService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        doReturn(new RouteDetailsDTO(1200.0, 5000.0, "~shnC~_rcL_@v@m@p@y@r@"))
                .when(mapboxAPIService).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // limpa a cada teste (obs: a ordem É IMPORTANTE)
        studentTravelRepository.deleteAll();
        travelRepository.deleteAll();
        studentRepository.deleteAll();
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
                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, true, 20.0, 10.0));

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

            // validar se é possível um teste para quando N É o firstPing

            @Test
            @DisplayName("should recalculate eta internally when vehicle is not offRoute ")
            void shouldRecalculateEtaInternallyWhenVehicleIsNotOffRoute() throws Exception {
                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, false, 20.0, 10.0));

                String key = "travelId:" + travelId;

                redisTemplate.opsForHash().put(key, "timestamp", Instant.now().toString());
                redisTemplate.opsForHash().put(key, "durationRemaining", "100.0");
                redisTemplate.opsForHash().put(key, "distanceRemaining", "500.0");

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                await().atMost(2, SECONDS).untilAsserted(() -> {
                    assertNotNull(redisTemplate.opsForHash().get(key, "durationRemaining"));
                    assertNotNull(redisTemplate.opsForHash().get(key, "distanceRemaining"));

                    verifyNoInteractions(mapboxAPIService);
                });
            }

            @Test
            @DisplayName("should process location after second (or more) ping")
            void shouldProcessLocationAfterSecondPingOrMore() throws Exception {
                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, false, 20.0, 10.0));

                String key = "travelId:" + travelId;

                // getLastLocation
                redisTemplate.opsForHash().put(key, "last_ping_lat", "12.974");
                redisTemplate.opsForHash().put(key, "last_ping_lng", "-38.501");
                redisTemplate.opsForHash().put(key, "last_ping_timestamp", String.valueOf(Instant.now()
                        .minusSeconds(10).toEpochMilli()));

                // getLastMovementState
                redisTemplate.opsForHash().put(key, "movementState", MovementState.NORMAL.name());
                redisTemplate.opsForHash().put(key, "stateStartedAt", Instant.now().toString());

                // getPreviousEta
                redisTemplate.opsForHash().put(key, "etaTimestamp", String.valueOf(Instant.now()
                        .minusSeconds(35).toEpochMilli()));
                redisTemplate.opsForHash().put(key, "durationRemaining", "100.0");
                redisTemplate.opsForHash().put(key, "distanceRemaining", "500.0");

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                await().atMost(5, SECONDS).untilAsserted(() -> {
                    String movementState = (String) redisTemplate.opsForHash().get(key, "movementState");
                    double updatedDuration = Double.parseDouble((String) Objects.requireNonNull(redisTemplate.opsForHash()
                            .get(key, "durationRemaining")));
                    double updateDistance = Double.parseDouble((String) Objects.requireNonNull(redisTemplate.opsForHash()
                            .get(key, "distanceRemaining")));

                    assertNotNull(movementState);

                    assertTrue(updatedDuration >= 0);
                    assertTrue(updateDistance >= 0);
                });
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

            @ParameterizedTest
            @DisplayName("throw exception when mapbox returns routeDetails with null or invalid fields ")
            @MethodSource("nullRouteDetailsProvider")
            void throwExceptionWhenMapboxReturnsInvalidRouteDetails(RouteDetailsDTO routeDetailsDTO) throws Exception {
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                String key = "travelId:" + travelId;

                // aguarda metodo async rodar e o salvamento incial
                await().atMost(3, SECONDS).untilAsserted(() -> {
                    String savedLat =  (String) redisTemplate.opsForHash().get(key, "last_calc_lat");
                    assertEquals(requestDTO.latitude().toString(), savedLat);
                });

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // redis metada não deve existir nesse contexto
                assertNull(hashOps.get(key, "distanceRemaining"));
                assertNull(hashOps.get(key, "durationRemaining"));
            }

            public static Stream<Arguments> nullRouteDetailsProvider() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(null, 1002.0, "encoded_polyline_route")),
                        Arguments.of(new RouteDetailsDTO(50.8, null, "encoded_polyline_route")),
                        Arguments.of((RouteDetailsDTO) null)
                );
            }

            @Test
            @DisplayName("throw exception and not processing when previousETA returns null fields from Redis")
            void shouldStopProcessingWhenPreviousEtaIsInvalidAndThrowException() throws Exception {
                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, false, 20.0, 10.0));

                mockMvc.perform(post("/travel/tracking/locationUpdate/{cityId}/{travelId}", cityId, travelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                        .andExpect(status().isOk());

                String key = "travelId:" + travelId;

                // aguarda execucao do metodo async e salvamento no banco
                await().during(2, SECONDS)
                        .atMost(3, SECONDS).untilAsserted(() -> {
                            assertNull(redisTemplate.opsForHash().get(key, "distanceRemaining"));
                            assertNull(redisTemplate.opsForHash().get(key, "durationRemaining"));
                        });

                verifyNoInteractions(mapboxAPIService);
            }
        }

    }

    @Nested
    class getDriverPosition {
        UUID cityId;
        UUID travelId;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

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

            student = new Student(
                    null,
                    "student@gmail.com",
                    "senhaSegura123",
                    "Student",
                    "Teste",
                    "75999999999",
                    "teste_img",
                    GeneralStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    InstitutionType.UNIVERSITY,
                    "Ciência da Computação"
            );
            studentRepository.save(student);

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

            studentTravel = new StudentTravel();
            studentTravel.setStudent(student);
            studentTravel.setEmbark(false);
            studentTravel.setTravel(travel);
            studentTravelRepository.save(studentTravel);
        }

        @Nested
        class successTestScenarios {

            @Test
            @DisplayName("should return driver position when isRouteOff returns TRUE with success")
            void shouldReturnDriverPositonWhenIsRouteOffReturnsTrueWithSuccess() throws Exception {
                String key = "travelId:" + travelId;

                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, true, 20.0, 10.0));
                when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(new RouteDetailsDTO(1200.0, 5000.0, "new_encoded_polyline"));

                // getLiveLocation
                redisTemplate.opsForHash().put(key, "lat", "-13.432");
                redisTemplate.opsForHash().put(key, "lng", "-39.843");
                redisTemplate.opsForHash().put(key, "geometry", "encoded_polyline_route");
                redisTemplate.opsForHash().put(key, "distance", "500.0");
                redisTemplate.opsForHash().put(key, "last_calc_lat", "12.974");
                redisTemplate.opsForHash().put(key, "last_calc_lng", "-38.501");


                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.latitude").value(-13.432))
                        .andExpect(jsonPath("$.longitude").value(-39.843))
                        .andExpect(jsonPath("$.geometry").value("new_encoded_polyline"))
                        .andExpect(jsonPath("$.distance").value(5000.0));


                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                assertEquals("new_encoded_polyline", hashOps.get(key, "geometry"));
                assertEquals("5000.0", hashOps.get(key, "distanceRemaining"));
                assertEquals("-13.432", hashOps.get(key, "last_calc_lat"));
                assertEquals("-39.843", hashOps.get(key, "last_calc_lng"));
            }

            @Test
            @DisplayName("should return driver position when isRouteOff returns FALSE with success")
            void shouldReturnDriverPositionWhenIsRouteOffReturnsFalseWithSuccess() throws Exception {
                String key = "travelId:" + travelId;

                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, false, 20.0, 10.0));

                // getLiveLocation
                redisTemplate.opsForHash().put(key, "lat", "-13.432");
                redisTemplate.opsForHash().put(key, "lng", "-39.843");
                redisTemplate.opsForHash().put(key, "geometry", "encoded_polyline_route");
                redisTemplate.opsForHash().put(key, "distance", "500.0");
                redisTemplate.opsForHash().put(key, "last_calc_lat", "12.974");
                redisTemplate.opsForHash().put(key, "last_calc_lng", "-38.501");


                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());


                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                assertEquals("encoded_polyline_route", hashOps.get(key, "geometry"));
                assertEquals("500.0", hashOps.get(key, "distance"));
                assertEquals("12.974", hashOps.get(key, "last_calc_lat"));
                assertEquals("-38.501", hashOps.get(key, "last_calc_lng"));

                verifyNoMoreInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("should recalculate route when Geometry data is null")
            void shouldRecalculateRouteWhenGeometryDataIsNull() throws Exception {
                String key = "travelId:" + travelId;

                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, false, 20.0, 10.0));
                when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(new RouteDetailsDTO(1200.0, 5000.0, "new_encoded_polyline"));

                // getLiveLocation
                redisTemplate.opsForHash().put(key, "lat", "-13.432");
                redisTemplate.opsForHash().put(key, "lng", "-39.843");
                // aqui não envia o geometry para o redis tratar como null
                redisTemplate.opsForHash().put(key, "distance", "500.0");
                redisTemplate.opsForHash().put(key, "last_calc_lat", "12.974");
                redisTemplate.opsForHash().put(key, "last_calc_lng", "-38.501");


                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.latitude").value(-13.432))
                        .andExpect(jsonPath("$.longitude").value(-39.843))
                        .andExpect(jsonPath("$.distance").value(5000.0));


                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                assertEquals("new_encoded_polyline", hashOps.get(key, "geometry"));
                assertEquals("5000.0", hashOps.get(key, "distanceRemaining"));
                assertEquals("-13.432", hashOps.get(key, "last_calc_lat"));
                assertEquals("-39.843", hashOps.get(key, "last_calc_lng"));
            }
        }

        @Nested
        class failureTestScenarios {

            @ParameterizedTest
            @DisplayName("throw exception when require data provides by liveLocation (redis) is null or invalid")
            @MethodSource("nullCurrentLocationProvider")
            void throwExceptionWhenRequireDataForLiveLocationIsNullOrInvalid(Map<String, String> redisData) throws Exception {
                String key = "travelId:" + travelId;

                redisData.forEach((field, value) -> redisTemplate.opsForHash().put(key, field, value));

                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId))
                        .andExpect(status().isNotFound());

                verifyNoInteractions(routeCalculationService, mapboxAPIService);
            }

            public static Stream<Arguments> nullCurrentLocationProvider() {
                return Stream.of(
                        Arguments.of(Map.of("lng", "-38.502321", "geometry", "polyline", "distance", "3875.40", "last_calc_lat", "-12.9728", "last_calc_lng", "-38.5017")),
                        Arguments.of(Map.of("lat", "-12.973456", "geometry", "polyline", "distance", "3875.40", "last_calc_lat", "-12.9728", "last_calc_lng", "-38.5017")),
                        Arguments.of(Map.of("lat", "-12.973456", "lng", "-38.502321", "geometry", "polyline", "last_calc_lat", "-12.9728", "last_calc_lng", "-38.5017")),
                        Arguments.of(Map.of("lat", "-12.973456", "lng", "-38.502321", "geometry", "polyline", "distance", "3875.40", "last_calc_lng", "-38.5017")),
                        Arguments.of(Map.of("lat", "-12.973456", "lng", "-38.502321", "geometry", "polyline", "distance", "3875.40", "last_calc_lat", "-12.9728")),
                        Arguments.of(Map.of())
                );
            }

            @Test
            void throwExceptionWhenTravelNotFound() throws Exception {
                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", UUID.randomUUID()))
                        .andExpect(status().isNotFound());

                verifyNoInteractions(routeCalculationService, mapboxAPIService);
            }

            @Test
            void throwExceptionWhenTravelIsNotTravelling() throws Exception {
                travel.setTravelStatus(TravelStatus.FINISH);
                travelRepository.save(travel);

                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId))
                        .andExpect(status().isConflict());

                verifyNoInteractions(routeCalculationService, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("throw exception when calculate route (mapbox api) returns null or invalid values")
            @MethodSource("nullRouteDetailsProvider")
            void throwExceptionWhenCalculateRouteReturnsNullOrInvalidValues(RouteDetailsDTO routeDetailsDTO) throws Exception {
                String key = "travelId:" + travelId;

                when(routeCalculationService.isRouteDeviation(anyDouble(), anyDouble(), any()))
                        .thenReturn(new RouteDeviationDTO(372.3, true, 20.0, 10.0));

                when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);

                // getLiveLocation
                redisTemplate.opsForHash().put(key, "lat", "-13.432");
                redisTemplate.opsForHash().put(key, "lng", "-39.843");
                redisTemplate.opsForHash().put(key, "geometry", "encoded_polyline_route");
                redisTemplate.opsForHash().put(key, "distance", "500.0");
                redisTemplate.opsForHash().put(key, "last_calc_lat", "12.974");
                redisTemplate.opsForHash().put(key, "last_calc_lng", "-38.501");

                mockMvc.perform(get("/travel/tracking/fastview/{travelId}", travelId)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadGateway());
            }

            public static Stream<Arguments> nullRouteDetailsProvider() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(null, 500.0, "encoded_polyline")),
                        Arguments.of(new RouteDetailsDTO(15.0, null, "encoded_polyline")),
                        Arguments.of(new RouteDetailsDTO(15.0, 500.0, null)),
                        Arguments.of((RouteDetailsDTO) null)
                );
            }
        }
    }

    @Nested
    class confirmEmbarkOnTravel {
        UUID cityId;
        UUID travelId;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

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

            student = new Student(
                    null,
                    "student@gmail.com",
                    "senhaSegura123",
                    "Student",
                    "Teste",
                    "75999999999",
                    "teste_img",
                    GeneralStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    InstitutionType.UNIVERSITY,
                    "Ciência da Computação"
            );
            studentRepository.save(student);

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

            studentTravel = new StudentTravel();
            studentTravel.setStudent(student);
            studentTravel.setEmbark(false);
            studentTravel.setTravel(travel);
            studentTravelRepository.save(studentTravel);
        }

        @Test
        void shouldConfirmStudentEmbarkOnTravelWithSuccess() throws Exception {
            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.save(travel);

            mockMvc.perform(post("/travel/tracking/confirmEmbark/{studentId}/{travelId}", student.getId(), travelId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            StudentTravel update = studentTravelRepository.findByStudentIdAndTravelId(student.getId(), travelId).orElseThrow();

            assertTrue(update.isEmbark());
        }

        @Test
        void throwExceptionWhenTravelStudentAssociationNotFound() throws Exception {
            studentTravel.setTravel(null);
            studentTravelRepository.save(studentTravel);

            mockMvc.perform(post("/travel/tracking/confirmEmbark/{studentId}/{travelId}", student.getId(), travelId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());

        }

        @Test
        @DisplayName("throw exception when student already embark on trip, and student-travel association exists")
        void throwExceptionWhenStudentAlreadyEmbark() throws Exception {
            studentTravel.setTravel(travel);
            studentTravel.setEmbark(true);
            studentTravelRepository.save(studentTravel);

            mockMvc.perform(post("/travel/tracking/confirmEmbark/{studentId}/{travelId}", student.getId(), travelId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

        }
    }

    @Nested
    class getTravelHistory {
        UUID cityId;
        UUID travelId;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

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

            student = new Student(
                    null,
                    "student@gmail.com",
                    "senhaSegura123",
                    "Student",
                    "Teste",
                    "75999999999",
                    "teste_img",
                    GeneralStatus.ACTIVE,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    InstitutionType.UNIVERSITY,
                    "Ciência da Computação"
            );
            studentRepository.save(student);

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

            studentTravel = new StudentTravel();
            studentTravel.setStudent(student);
            studentTravel.setEmbark(false);
            studentTravel.setTravel(travel);
            studentTravelRepository.save(studentTravel);
        }

        @Test
        void shouldGetTravelHistoryWithSuccess() throws Exception {
            TravelLocationHistory history = new TravelLocationHistory(
                    travelId, cityId,
                    -12.973456, -38.501234,
                    Instant.now());

            travelLocationHistoryRepository.save(history);

            mockMvc.perform(get("/travel/tracking/{travelId}/historyPoints", travelId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andDo(print())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].latitude").value(-12.973456))
                    .andExpect(jsonPath("$.content[0].longitude").value(-38.501234))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }

        @Test
        void shouldReturnAnEmptyPageWhenHistoricAreNull() throws Exception {
            mockMvc.perform(get("/travel/tracking/{travelId}/historyPoints", travelId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.page.totalElements").value(0))
                    .andExpect(jsonPath("$.page.totalPages").value(0));
        }

    }
}