package com.travel_system.backend_app.integration.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mapbox.geojson.Point;
import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.GpsDataIngestorService;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.RouteCalculationService;
import com.travel_system.backend_app.service.TravelTrackingService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelTrackingControllerIT extends IntegrationTestBase {

    private final Logger logger = LoggerFactory.getLogger(TravelTrackingControllerIT.class);

    private static final String TRACKING_KEY_PREFIX = "travel:tracking:";
    private static final String ROUTE_KEY_PREFIX    = "travel:route:";

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
    @Autowired
    private GeoPositionRepository geoPositionRepository;

    @MockitoBean
    private RouteCalculationService routeCalculationService;
    @MockitoBean
    private RedisTrackingService redisTrackingService;

    @MockitoSpyBean
    private TravelTrackingService travelTrackingService;

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
        RouteDetailsDTO routeDetailsDTO;
        RouteDeviationDTO routeDeviationDTO;

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
                    "Salvador", 0, new ArrayList<>(), null);
            driver.setPermissions(List.of(permission));
            driverRepository.save(driver);

             travel = new Travel(
                    null, city, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);
            travel.setStudentTravels(new HashSet<>());
            travelId = travel.getId();

            requestDTO = new VehicleLocationRequestDTO(travelId, -12.9750, -38.5020, 60.0, 180.0);
            routeDetailsDTO = new RouteDetailsDTO(3100.0, 14500.0, "recalculated_polyline");
            routeDeviationDTO = new RouteDeviationDTO(325.0, true, -12.9708, -38.4986);


        }

        @Nested
        class successTestScenarios {

            @Test
            @DisplayName("Deve realizar o primeiro cálculo de rota (sem referência armazenada no Redis)")
            void shouldInitializeRouteCalculationStateWhenNoPreviousRouteReferenceExists() throws Exception {
                String trackingKey = TRACKING_KEY_PREFIX + travelId;
                String routeKey    = ROUTE_KEY_PREFIX    + travelId;

                assertFalse(redisTemplate.hasKey(trackingKey));
                assertFalse(redisTemplate.hasKey(routeKey));

                RouteDetailsDTO routeDetailsDTO = new RouteDetailsDTO(3300.0, 200.0, "encoded_polyline_test");

                when(mapboxAPIService.recalculateETA(
                        requestDTO.longitude(),
                        requestDTO.latitude(),
                        travel.getFinalLongitude(),
                        travel.getFinalLatitude()))
                        .thenReturn(routeDetailsDTO);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                Map<String, String> trackingData = hashOps.entries(trackingKey);
                Map<String, String> routeData = hashOps.entries(routeKey);

                // tracking assertions
                assertFalse(trackingData.isEmpty());

                assertEquals(trackingData.get("current_lat"), requestDTO.latitude().toString());
                assertEquals(trackingData.get("current_lng"), requestDTO.longitude().toString());
                assertEquals(trackingData.get("current_speed"), requestDTO.speed().toString());
                assertEquals(trackingData.get("current_heading"), requestDTO.heading().toString());

                assertNotNull(trackingData.get("current_location_timestamp"));

                // route assertions
                assertFalse(routeData.isEmpty());

                assertEquals(routeData.get("last_calc_lat"), requestDTO.latitude().toString());
                assertEquals(routeData.get("last_calc_lng"), requestDTO.longitude().toString());
                assertEquals(routeData.get("distanceRemaining"), routeDetailsDTO.distance().toString());
                assertEquals(routeData.get("geometry"), routeDetailsDTO.geometry());

                verify(mapboxAPIService, times(1)).recalculateETA(
                        requestDTO.longitude(),
                        requestDTO.latitude(),
                        travel.getFinalLongitude(),
                        travel.getFinalLatitude());

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });
            }

            @Test
            @DisplayName("Recalculo de rota requisitado pela distância")
            void shouldRecalculateRouteWhenDistanceThresholdIsReached() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;
                String trackingKey = TRACKING_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // storeCalculatedRouteState
                hashOps.put(routeKey, "last_calc_lat",       "-12.9714");
                hashOps.put(routeKey, "last_calc_lng",       "-38.5016");
                hashOps.put(routeKey, "distanceRemaining",   "15000.0");
                hashOps.put(routeKey, "geometry",            "encoded_polyline_initial");

                // assert na população do redis
                assertTrue(redisTemplate.hasKey(routeKey), "O hash de rota deve existir antes do request");
                assertEquals("-12.9714", hashOps.get(routeKey, "last_calc_lat"));
                assertEquals("-38.5016", hashOps.get(routeKey, "last_calc_lng"));

                VehicleLocationRequestDTO newPingDTO = new VehicleLocationRequestDTO(travelId, -12.9708, -38.5016, 55.0, 90.0);
                RouteDeviationDTO offRouteDeviation = new RouteDeviationDTO(325.0, true, -12.9708, -38.4986);
                RouteDetailsDTO recalculatedRoute = new RouteDetailsDTO(3100.0, 14500.0, "recalculated_polyline");

                when(mapboxAPIService.recalculateETA(
                        newPingDTO.longitude(),
                        newPingDTO.latitude(),
                        travel.getFinalLongitude(),
                        travel.getFinalLatitude()))
                        .thenReturn(recalculatedRoute);

                when(routeCalculationService.isRouteDeviation(
                        any(RouteDeviationRequestDTO.class)))
                        .thenReturn(offRouteDeviation);

                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        newPingDTO.latitude(),
                        newPingDTO.longitude(),
                        -12.9714,
                        -38.5016))
                        .thenReturn(66.7);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newPingDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                Map<String, String> trackingData = hashOps.entries(trackingKey);

                // tracking
                assertFalse(trackingData.isEmpty());

                assertEquals(newPingDTO.latitude().toString(),  trackingData.get("current_lat"));
                assertEquals(newPingDTO.longitude().toString(), trackingData.get("current_lng"));
                assertEquals(newPingDTO.speed().toString(),     trackingData.get("current_speed"));
                assertEquals(newPingDTO.heading().toString(),   trackingData.get("current_heading"));

                assertNotNull(trackingData.get("current_location_timestamp"));

                // route
                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertEquals(newPingDTO.latitude().toString(), routeData.get("last_calc_lat"));
                assertEquals(newPingDTO.longitude().toString(), routeData.get("last_calc_lng"));
                assertEquals(recalculatedRoute.distance().toString(), routeData.get("distanceRemaining"));
                assertEquals(recalculatedRoute.geometry(), routeData.get("geometry"));

                verify(mapboxAPIService, times(1)).recalculateETA(
                        newPingDTO.longitude(),
                        newPingDTO.latitude(),
                        travel.getFinalLongitude(),
                        travel.getFinalLatitude());

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });
            }

            @Test
            @DisplayName("Deve processar a localização sem realizar recalculo de rota")
            void shouldProcessLocationWithoutRouteRecalculationWhenDriverIsWithinThreshold() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // storeCalculatedRouteState
                hashOps.put(routeKey, "last_calc_lat", "-12.9714");
                hashOps.put(routeKey, "last_calc_lng", "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry", "encoded_polyline_initial");

                // sem desvio de rota
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016
                        )).thenReturn(40.5);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                // route
                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertEquals(String.valueOf(-12.9714), routeData.get("last_calc_lat"));
                assertEquals(String.valueOf(-38.5016), routeData.get("last_calc_lng"));
                assertEquals(String.valueOf(15000.0), routeData.get("distanceRemaining"));
                assertEquals("encoded_polyline_initial", routeData.get("geometry"));

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });

                verify(routeCalculationService, times(2)).calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016);

                verify(mapboxAPIService, never()).recalculateETA(any(), any(), any(), any());
                verify(routeCalculationService, never()).isRouteDeviation(any());
            }

            @Test
            @DisplayName("Motorista está além do limiar permitido, mas sem desvio de rota")
            void shouldSkipMapboxRecalculationWhenDriverIsOnRouteAndGeometryExists() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // storeCalculatedRouteState
                hashOps.put(routeKey, "last_calc_lat", "-12.9714");
                hashOps.put(routeKey, "last_calc_lng", "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry", "encoded_polyline_initial");

                // assert na população do redis
                assertTrue(redisTemplate.hasKey(routeKey), "O hash de rota deve existir antes do request");
                assertEquals("-12.9714", hashOps.get(routeKey, "last_calc_lat"));
                assertEquals("-38.5016", hashOps.get(routeKey, "last_calc_lng"));

                RouteDeviationDTO newRouteDeviation = new RouteDeviationDTO(325.0, false, -12.9708, -38.4986);

                // com desvio de rota
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016
                )).thenReturn(55.5);

                when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, requestDTO.latitude(), requestDTO.longitude())))
                        .thenReturn(newRouteDeviation);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                // route
                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertEquals(String.valueOf(-12.9714), routeData.get("last_calc_lat"));
                assertEquals(String.valueOf(-38.5016), routeData.get("last_calc_lng"));
                assertEquals(String.valueOf(15000.0), routeData.get("distanceRemaining"));
                assertEquals("encoded_polyline_initial", routeData.get("geometry"));

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });

                verify(routeCalculationService, times(2)).calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016);

                verify(routeCalculationService, times(2)).isRouteDeviation(
                        new RouteDeviationRequestDTO(
                                travelId,
                                requestDTO.latitude(),
                                requestDTO.longitude()));

                verify(mapboxAPIService, never()).recalculateETA(any(), any(), any(), any());
            }

            @Test
            @DisplayName("Evento Async deve ser processado normalmente quando os dados são válidos")
            void shouldProcessLocationEventSuccessfullyWhenValidEventIsReceived() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                hashOps.put(routeKey, "last_calc_lat",     "-12.9714");
                hashOps.put(routeKey, "last_calc_lng",     "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry",          "encoded_polyline_initial");

                long nowMillis = Instant.now().toEpochMilli();
                hashOps.put(routeKey, "durationRemaining", "3000.0");
                hashOps.put(routeKey, "etaTimestamp",      String.valueOf(nowMillis));

                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016))
                        .thenReturn(40.5);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            // os três métodos devem ter sido chamados
                            verify(travelTrackingService, atLeastOnce())
                                    .processNewLocation(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });

                verify(travelTrackingService, times(1)).processNewLocation(
                        new VehicleLocationRequestDTO(
                                travelId,
                                requestDTO.latitude(),
                                requestDTO.longitude(),
                                requestDTO.speed(),
                                requestDTO.heading()));

                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertNotNull(routeData.get("durationRemaining"));

                assertNotNull(routeData.get("metadataUpdatedAt"));

                assertEquals(TravelStatus.TRAVELLING.toString(), routeData.get("status"));

                verify(mapboxAPIService, never()).recalculateETA(any(), any(), any(), any());
                verify(routeCalculationService, never()).isRouteDeviation(any());
            }

            @Test
            @DisplayName("Estudante dentro da área esperada, não deve haver auto-disconnect")
            void shouldNotMarkStudentAsAwayWhenStudentIsWithinExpectedArea() throws Exception {
                Student student = new Student(null, "student@gmail.com", "senhaSegura123", "Student", "Teste", "75999999999", "teste_img", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), InstitutionType.UNIVERSITY, "Ciência da Computação");
                studentRepository.save(student);

                GeoPosition geoPosition = new GeoPosition(null, 12.9750, -38.5020, Instant.now(), null);
                geoPositionRepository.save(geoPosition);

                StudentTravel studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, geoPosition, StudentTravelStatus.ACTIVE);
                studentTravelRepository.save(studentTravel);

                travel.setStudentTravels(Set.of(studentTravel));
                travelRepository.save(travel);


                String routeKey    = ROUTE_KEY_PREFIX    + travelId;
                String trackingKey = TRACKING_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                hashOps.put(routeKey, "last_calc_lat",     "-12.9714");
                hashOps.put(routeKey, "last_calc_lng",     "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry",          "encoded_polyline_initial");

                // storeCurrentLocation — lido por extractLiveCoordinates dentro do markDriverCheckpoint
                hashOps.put(trackingKey, "current_lat", String.valueOf(requestDTO.latitude()));
                hashOps.put(trackingKey, "current_lng", String.valueOf(requestDTO.longitude()));

                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016))
                        .thenReturn(40.5);

                // distância driver → estudante (abaixo do AUTO_DISCONNECT_DISTANCE_METERS=350)
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        eq(requestDTO.latitude()),
                        eq(requestDTO.longitude()),
                        eq(studentTravel.getPosition().getLatitude()),
                        eq(studentTravel.getPosition().getLongitude())))
                        .thenReturn(70.0);


                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                StudentTravel studentTravelAfter = studentTravelRepository.findById(studentTravel.getId())
                        .orElseThrow();

                assertEquals(StudentTravelStatus.ACTIVE, studentTravelAfter.getStudentTravelStatus());

                String studentTravelKey = "travel:student:" + student.getId() + ":" + travelId;

                assertNull(redisTemplate.opsForHash().get(studentTravelKey, "studentAwayTimestamp"));

                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> {
                            verify(pushNotificationService, atLeastOnce())
                                    .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                            verify(pushNotificationService, atLeastOnce())
                                    .processVehicleMovement(any(VehicleLocationRequestDTO.class));
                        });

                verify(mapboxAPIService, never()).recalculateETA(any(), any(), any(), any());

            }
        }

        @Nested
        class failureTestScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o ID da URL for direfere do ID do DTO recebido no body")
            void throwExceptionWhenPathTravelIdDiffersFromRequestBodyTravelId() throws Exception {
                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", UUID.randomUUID(), cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isBadRequest());
            }

            @Test
            void throwExceptionWhenTripNotFound() throws Exception {
                UUID newTravelId = UUID.randomUUID();

                VehicleLocationRequestDTO newDto = new VehicleLocationRequestDTO(newTravelId, -12.9750, -38.5020, 60.0, 180.0);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", newTravelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newDto)))
                        .andDo(print())
                        .andExpect(status().isNotFound());
            }

            @ParameterizedTest
            @MethodSource("statusProvider")
            void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
                travel.setTravelStatus(travelStatus);
                travelRepository.save(travel);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isConflict());
            }

            public static Stream<Arguments> statusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.PENDING),
                        Arguments.of(TravelStatus.FINISH)
                );
            }

            @ParameterizedTest
            @DisplayName("deve lançar exception quando a chamada da API retornar dados null ou inválidos")
            @MethodSource("nullRouteDetailsFieldsProvider")
            void throwExceptionWhenRecalculateEtaReturnsNullOrInvalidData(RouteDetailsDTO routeDetailsDTO) throws Exception {
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isBadGateway());

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                String routeKey = ROUTE_KEY_PREFIX + travelId;

                // não deve ter salvo nada no redis
                assertNull(hashOps.get(routeKey, "last_calc_lat"));
                assertNull(hashOps.get(routeKey, "last_calc_lng"));
                assertNull(hashOps.get(routeKey, "geometry"));
                assertNull(hashOps.get(routeKey, "distanceRemaining"));
            }

            public static Stream<Arguments> nullRouteDetailsFieldsProvider() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(300.3, null, "encoded_geometry")),
                        Arguments.of(new RouteDetailsDTO(300.3, 4030.0, null)),
                        Arguments.of((RouteDetailsDTO) null)
                );
            }

            @Test
            void throwException500ServerErrorWhenWithoutConnectionRedis() throws Exception {
                when(redisTrackingService.getRouteCalculateReference(travelId))
                        .thenThrow(new RedisConnectionFailureException("without connection with redis"));

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isInternalServerError());
            }

            @Test
            @DisplayName("falha em processamento async não deve afetar o http 200OK ja retornado")
            void shouldStopAsyncProcessingWhenProcessNewLocationThrowsException() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // storeCalculatedRouteState
                hashOps.put(routeKey, "last_calc_lat", "-12.9714");
                hashOps.put(routeKey, "last_calc_lng", "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry", "encoded_polyline_initial");

                when(redisTrackingService.getLiveLocation(travelId))
                        .thenReturn(new LiveLocationDTO(
                                -12.9714,
                                -38.5016,
                                "encoded_polyline_initial",
                                550.0, -12.9901, -38.5201));

                // sem desvio de rota
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016
                )).thenReturn(40.5);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                // route
                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertEquals(String.valueOf(-12.9714), routeData.get("last_calc_lat"));
                assertEquals(String.valueOf(-38.5016), routeData.get("last_calc_lng"));
                assertEquals(String.valueOf(15000.0), routeData.get("distanceRemaining"));
                assertEquals("encoded_polyline_initial", routeData.get("geometry"));

                // lança exception nos métodos async
                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> doThrow(new RuntimeException()).when(pushNotificationService)
                                .processVehicleMovement(any(VehicleLocationRequestDTO.class)));
            }

            @Test
            void shouldStopAsyncProcessingWhenCheckProximityAlertsThrowsException() throws Exception {
                String routeKey = ROUTE_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                // storeCalculatedRouteState
                hashOps.put(routeKey, "last_calc_lat", "-12.9714");
                hashOps.put(routeKey, "last_calc_lng", "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry", "encoded_polyline_initial");

                when(redisTrackingService.getLiveLocation(travelId))
                        .thenReturn(new LiveLocationDTO(
                                -12.9714,
                                -38.5016,
                                "encoded_polyline_initial",
                                550.0, -12.9901, -38.5201));

                // sem desvio de rota
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016
                )).thenReturn(40.5);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                // route
                Map<String, String> routeData = hashOps.entries(routeKey);

                assertFalse(routeData.isEmpty());

                assertEquals(String.valueOf(-12.9714), routeData.get("last_calc_lat"));
                assertEquals(String.valueOf(-38.5016), routeData.get("last_calc_lng"));
                assertEquals(String.valueOf(15000.0), routeData.get("distanceRemaining"));
                assertEquals("encoded_polyline_initial", routeData.get("geometry"));

                // lança exception no método async
                Awaitility.await()
                        .atMost(3, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() -> doThrow(new RuntimeException()).when(pushNotificationService)
                                .checkProximityAlerts(any(VehicleLocationRequestDTO.class)));
            }

            @Test
            @DisplayName("Deve desconectar o estudante pelo algoritmo de auto-disconnect")
            void shouldAutoDisconnectStudentWhenStudentRemainsFarAwayForExtendedPeriod() throws Exception {
                Student student = new Student(null, "student@gmail.com", "senhaSegura123", "Student", "Teste", "75999999999", "teste_img", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), InstitutionType.UNIVERSITY, "Ciência da Computação");
                studentRepository.save(student);

                GeoPosition geoPosition = new GeoPosition(null, 21.9750, -58.5020, Instant.now(), null);
                geoPositionRepository.save(geoPosition);

                StudentTravel studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, geoPosition, StudentTravelStatus.ACTIVE);
                studentTravelRepository.save(studentTravel);

                travel.setStudentTravels(Set.of(studentTravel));
                travelRepository.save(travel);

                String routeKey    = ROUTE_KEY_PREFIX    + travelId;
                String trackingKey = TRACKING_KEY_PREFIX + travelId;

                HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

                hashOps.put(routeKey, "last_calc_lat",     "-12.9714");
                hashOps.put(routeKey, "last_calc_lng",     "-38.5016");
                hashOps.put(routeKey, "distanceRemaining", "15000.0");
                hashOps.put(routeKey, "geometry",          "encoded_polyline_initial");

                // storeCurrentLocation — lido por extractLiveCoordinates dentro do markDriverCheckpoint
                hashOps.put(trackingKey, "current_lat", String.valueOf(requestDTO.latitude()));
                hashOps.put(trackingKey, "current_lng", String.valueOf(requestDTO.longitude()));

                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        requestDTO.latitude(),
                        requestDTO.longitude(),
                        -12.9714,
                        -38.5016))
                        .thenReturn(40.5);

                long oldTimestamp = Instant.now()
                        .minusMillis(TimeUnit.MINUTES.toMillis(5) + 1000)
                        .toEpochMilli();

                when(redisTrackingService.getStudentAwayTimestamp(
                        eq(travelId),
                        any(DistanceResponseDTO.class)))
                        .thenReturn(oldTimestamp);

                when(redisTrackingService.getLiveLocation(travelId))
                        .thenReturn(new LiveLocationDTO(
                                -12.9714,
                                -38.5016,
                                "encoded_polyline_initial",
                                550.0, -12.9901, -38.5201));

                // distância driver -> estudante (maior do que AUTO_DISCONNECT_DISTANCE_METERS=350)
                when(routeCalculationService.calculateHaversineDistanceInMeters(
                        anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(400.0);

                mockMvc.perform(post("/v1/tracking/travels/{travelId}/locations/{cityId}", travelId, cityId)
                                .with(user("authenticated_user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDTO)))
                        .andDo(print())
                        .andExpect(status().isOk());

                StudentTravel studentTravelAfter = studentTravelRepository.findById(studentTravel.getId())
                        .orElseThrow();

                assertEquals(
                        StudentTravelStatus.AUTO_DISCONNECTED,
                        studentTravelAfter.getStudentTravelStatus());

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
                    "Salvador", 0, new ArrayList<>(), new City());
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
                    null, city, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
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

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
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

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
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

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
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

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
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
                    "Salvador", 0, new ArrayList<>(), new City());
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
                    null, city, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
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
                    "Salvador", 0, new ArrayList<>(), new City());
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
                    null, city, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
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