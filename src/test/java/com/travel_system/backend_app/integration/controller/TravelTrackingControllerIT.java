package com.travel_system.backend_app.integration.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.listeners.StudentAwayStateListener;
import com.travel_system.backend_app.listeners.VehicleGpsListener;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
//@Transactional
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
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private GeoPositionRepository geoPositionRepository;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RedisTrackingService redisTrackingService;

    private CircuitBreaker circuitBreaker;

    @MockitoBean
    private RouteCalculationService routeCalculationService;
    @MockitoBean
    private TravelCacheService travelCacheService;
    @MockitoBean
    private TravelTrackingNotificationService trackingNotificationService;

    @MockitoSpyBean
    private GpsDataIngestorService gpsDataIngestorService;
    @MockitoSpyBean
    private TravelTrackingService travelTrackingService;

    @MockitoBean
    private Clock clock;

    private String DEFAULT_CONTROLLER_PATH = "/v1/tracking";
    private String authUser = "authenticated_user";
    private String driverRole = "ROLE_DRIVER";

    private static String ROUTE_KEY_PREFIX;
    private static String TRACKING_KEY_PREFIX;
    private static String STUDENT_TRAVEL_KEY_PREFIX;

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();

        entityManager.clear();

        geoPositionRepository.deleteAllInBatch();
        studentTravelRepository.deleteAllInBatch();
        travelRepository.deleteAllInBatch();
        studentRepository.deleteAllInBatch();
        driverRepository.deleteAllInBatch();
        permissionsRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        cityRepository.deleteAllInBatch();
    }

/*    @BeforeEach
    void setUp() {
        // limpa a cada teste (obs: a ordem É IMPORTANTE)
        geoPositionRepository.deleteAll();
        studentTravelRepository.deleteAll();
        travelRepository.deleteAll();
        studentRepository.deleteAll();
        driverRepository.deleteAll();
        permissionsRepository.deleteAll();
        customerRepository.deleteAll();
        cityRepository.deleteAll();

        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
        
    }*/

    @Nested
    class markDriverCheckpoint {
        City city;
        Customer customer;
        Driver driver;
        StudentTravel studentTravel;
        GeoPosition position;
        Travel travel;
        Student student;

        VehicleLocationRequestDTO requestDTO;
        RouteDetailsDTO routeDetailsDTO;
        RouteDeviationDTO routeDeviationDTO;
        TravelCacheDTO travelCacheDTO;

        String completePathController;

       @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            Mockito.reset(pushNotificationService);

            circuitBreaker = circuitBreakerRegistry.circuitBreaker("gpsIngestor");
            circuitBreaker.transitionToDisabledState();
            circuitBreaker.reset(); // zera os contadores

            Permissions permission = new Permissions("ROLE_DRIVER");
            permissionsRepository.save(permission);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driver.setPermissions(List.of(permission));
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer);
            travel = travelRepository.saveAndFlush(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravel = studentTravelRepository.saveAndFlush(studentTravel);

//            travel.setStudentTravels(Set.of(studentTravel));

            requestDTO = new VehicleLocationRequestDTO(travel.getId(), -12.9750, -38.5020, 60.0, 180.0);
            routeDetailsDTO = new RouteDetailsDTO(3100.0, 14500.0, "recalculated_polyline");
            routeDeviationDTO = new RouteDeviationDTO(325.0, true, -12.9708, -38.4986);
            travelCacheDTO = new TravelCacheDTO(travel.getId(), TravelStatus.TRAVELLING, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

            ROUTE_KEY_PREFIX = "travel:route:" + travel.getId();
            TRACKING_KEY_PREFIX = "travel:tracking:" + travel.getId();
            STUDENT_TRAVEL_KEY_PREFIX = "travel:away_students:" + travel.getId();

            completePathController = DEFAULT_CONTROLLER_PATH + "/travels/" + travel.getId() + "/locations/" + city.getId();
        }

        @Nested
        @DisplayName("Responsável por validar exclusivamente o comportamento do endpoint /locations/, sem validar de fato métodos async ou comportamento de eventos")
        class markDriverCheckpointFunctioningController {

           @Nested
           class successScenarios {

               @Test
               @DisplayName("Deve validar os cenários de cálculo de rota, armazenamento de infos no Redis e publicação de eventos de domínio no recebimento do primeiro ping GPS")
               void shouldFirstCheckpointRegistryWithSuccess() throws Exception {
                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    // storeCurrentLocation
                    assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                    assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                    assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                    assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                    // storeCalculatedRouteState
                    assertEquals(routeDetailsDTO.distance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                    assertEquals(routeDetailsDTO.geometry(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                    assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                    assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                    verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
               }

               @Test
               @DisplayName("Deve garantir que um novo checkpoint apenas atualize a localização do veículo quando não houver validação de rota novamente")
               void shouldNewCheckpointRegistryWithoutRecalculateRoute() throws Exception {
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                   // getLiveLocation
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(30.0);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   // storeCurrentLocation
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   verify(travelCacheService, times(2)).getOrLoadTravelStaticCache(travel.getId());
                   verify(routeCalculationService, times(2)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                   verifyNoInteractions(mapboxAPIService);
               }

               @Test
               @DisplayName("Deve garantir que ao atingir a distância mínima para validação o sistema consulte novamente a API do mapbox")
               void shouldRouteRecalculateWhenMinimumDistanceWasReached() throws Exception {
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                   // getLiveLocation
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(60.0);
                   when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(routeDeviationDTO);
                   doReturn(routeDetailsDTO).when(mapboxAPIService).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   // storeCurrentLocation
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   // storeCalculatedRouteState
                   assertEquals(routeDetailsDTO.distance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertEquals(routeDetailsDTO.geometry(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   verify(travelCacheService, times(2)).getOrLoadTravelStaticCache(travel.getId());
                   verify(routeCalculationService, times(2)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

               }

               @Test
               @DisplayName("Deve garantir que o sistema recalcule a rota quando o estado atual do redis não possuir o Geometry")
               void shouldRecalculateRouteWhenGeometryCachedIsNull() throws Exception {
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));
//                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");

                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(60.0);
                   when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(routeDeviationDTO);
                   doReturn(routeDetailsDTO).when(mapboxAPIService).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   // storeCurrentLocation
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   // storeCalculatedRouteState
                   assertEquals(routeDetailsDTO.distance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertEquals(routeDetailsDTO.geometry(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   verify(travelCacheService, times(2)).getOrLoadTravelStaticCache(travel.getId());
                   verify(routeCalculationService, times(2)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
               }

               @Test
               @DisplayName("Deve garantir que lat/lng/speed/heading sejam sempre atualizados independentemente da necessidade de recalculo de rota")
               void shouldUpgradeOnlyVehicleLocalization() throws Exception {
                   travel.setStudentTravels(Set.of(studentTravel));
                   travelRepository.save(travel);

                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                   // getLiveLocation
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(40.0);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   assertEquals(1, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(1, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(1, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }
           }

           @Nested
           class failureScenarios {

               @Test
               @DisplayName("Deve lançar exception quando houver inconsistência entre os identificadores")
               void shouldRejectWhenTravelIdInBodyDiffersFromPathVariable() throws Exception {
                   mockMvc.perform(post(DEFAULT_CONTROLLER_PATH + "/travels/" + UUID.randomUUID() + "/locations/" + city.getId())
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isBadRequest());

                   verifyNoInteractions(mapboxAPIService, routeCalculationService);

                    // não deve gerar nenhuma escrita no redis
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               @ParameterizedTest
               @DisplayName("Deve lançar exception quando os dados de coordenadas, speed e heading não forem válidos")
               @MethodSource("invalidVehicleLocationProvider")
               void shouldRejectWhenLatitudeLongitudeSpeedOrHeadingIsNull(VehicleLocationRequestDTO newVehicleLocRequest) throws Exception {
                   mockMvc.perform(post(DEFAULT_CONTROLLER_PATH + "/travels/" + UUID.randomUUID() + "/locations/" + city.getId())
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(newVehicleLocRequest)))
                           .andDo(print())
                           .andExpect(status().isBadRequest());

                   verifyNoInteractions(mapboxAPIService, routeCalculationService);

                   // não deve gerar nenhuma escrita no redis
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               public static Stream<Arguments> invalidVehicleLocationProvider() {
                   return Stream.of(
                           Arguments.of(new VehicleLocationRequestDTO(null, -12.9750, -38.5020, 60.0, 180.0)),
                           Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.5020, 60.0, 180.0)),
                           Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, null, 60.0, 180.0)),
                           Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, null, 180.0)),
                           Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, 60.0, null))
                   );
               }

               @Test
               @DisplayName("Deve lançar exception quando a viagem não existir, evitando processamento")
               void shouldRejectWhenTravelNotExists() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenThrow(new EntityNotFoundException());

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isNotFound());

                   verifyNoInteractions(mapboxAPIService, routeCalculationService);

                   // não deve gerar nenhuma escrita no redis
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               @ParameterizedTest
               @DisplayName("Deve garantir que apenas viagens inicializadas sejam processadas")
               @MethodSource("invalidTravelStatusProvider")
               void shouldRejectWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
                   TravelCacheDTO withInvalidStatus = new TravelCacheDTO(travel.getId(), travelStatus, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(withInvalidStatus);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isConflict());

                   verifyNoInteractions(mapboxAPIService, routeCalculationService);

                   // não deve gerar nenhuma escrita no redis
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               public static Stream<Arguments> invalidTravelStatusProvider() {
                   return Stream.of(
                           Arguments.of(TravelStatus.PENDING),
                           Arguments.of(TravelStatus.CANCELED),
                           Arguments.of(TravelStatus.FINISH)
                   );
               }

               @ParameterizedTest
               @DisplayName("Deve lançar exception quando o primeiro cálculo (mapbox response) retornar null")
               @MethodSource("invalidRouteDetailsDTO")
               void shouldThrowRecalculateEtaExceptionWhenFirstCalculationReturnsNull(RouteDetailsDTO invalidRouteDetailsDTO) throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(invalidRouteDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isBadGateway());

                   verifyNoInteractions(routeCalculationService);

                   // deve gerar escrita de ping
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   // não deve gerar escrita de rota recalculada
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               public static Stream<Arguments> invalidRouteDetailsDTO() {
                   return Stream.of(
                           Arguments.of(new RouteDetailsDTO(120.0, null, "encoded_geometry_polyline")),
                           Arguments.of(new RouteDetailsDTO(120.0, 5000.0, null)),
                           Arguments.of((RouteDetailsDTO) null)
                   );
               }

               @ParameterizedTest
               @DisplayName("Deve lançar exception quando algum erro ocorrer durante um recálculo também interrompam o processamento")
               @MethodSource("invalidRouteDetailsDTO") // reutiliza o provedor acima
               void shouldFailDuringRouteRecalculationWhenMapboxReturnsInvalidResponse(RouteDetailsDTO invalidRouteDetailsDTO) throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(60.0);
                   when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(routeDeviationDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(invalidRouteDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isBadGateway());

                   // deve gerar escrita de ping
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertNotNull(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   // não deve gerar escrita de rota recalculada
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                   assertNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }

               @Test
               @DisplayName("Deve garantir que o estado calculado da rota permaneça inalterado quando nenhuma regra exigir novo cálculo.")
               void shouldPreserveRouteStateWhenShouldRevalidateRouteIsFalse() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(20.0);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isBadGateway());

                   // deve gerar escrita de ping
                   assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
                   assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));
                   assertEquals(requestDTO.speed().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_speed"));
                   assertEquals(requestDTO.heading().toString(), redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_heading"));

                   // sem publicar evento
                   assertEquals(0, applicationEvents.stream(StudentAwayStateCheckEvent.class).count());
                   assertEquals(0, applicationEvents.stream(NewLocationReceivedEvents.class).count());
                   assertEquals(0, applicationEvents.stream(VehicleGpsMessageDTO.class).count());
               }
           }
        }

        @Nested
        @DisplayName("Validar que o endpoint publica corretamente todos os eventos de domínio necessários. Não valida efeitos async ou comportamento do síncrono do método")
        class validAllEventPublishing {

            @Nested
            class successScenarios {

                @Test
                @DisplayName("Após receber um novo PING válido o sistema deve publicar 'newLocationReceivedEvents' contendo as informações de localização")
                void shouldPublishNewLocationReceivedEventWhenCheckpointIsProcessed() throws Exception {
                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();

                    assertEquals(1, receivedEvents.size());

                    NewLocationReceivedEvents event = receivedEvents.get(0);

                    assertEquals(travel.getId(), event.travelId());
                    assertEquals(requestDTO.latitude(), event.latitude());
                    assertEquals(requestDTO.longitude(), event.longitude());
                    assertEquals(requestDTO.speed(), event.speed());
                    assertEquals(requestDTO.heading(), event.heading());
                    assertEquals(TravelStatus.TRAVELLING, event.status());

                    assertNotNull(event.timestamp());

                }

                @Test
                @DisplayName("Deve publicar um 'StudentAwayStateCheckEvent' para disparar o algoritmo de auto-desconexão")
                void shouldPublishStudentAwayStateCheckEventAfterCheckpoint() throws Exception {
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                    // getLiveLocation
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                    redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                    redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    List<StudentAwayStateCheckEvent> studentAwayState = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();

                    assertEquals(1, studentAwayState.size());

                    StudentAwayStateCheckEvent event = studentAwayState.getFirst();

                    assertEquals(travel.getId(), event.travelId());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"), event.liveLocationDTO().lastCalcLat().toString());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"), event.liveLocationDTO().lastCalcLng().toString());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"), event.liveLocationDTO().geometry());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"), event.liveLocationDTO().distance().toString());

                    assertEquals(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"), event.liveLocationDTO().latitude().toString());
                    assertEquals(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"), event.liveLocationDTO().longitude().toString());
                }

                @Test
                @DisplayName("Deve ser publicado um 'VehicleGpsMessageDTO' contendo todas as informações necessárias para envio ao broker.")
                void shouldPublishVehicleGpsMessageEventAfterCheckpoint() throws Exception {
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", String.valueOf(900.4));
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    List<VehicleGpsMessageDTO> vehicleEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();

                    assertEquals(1, vehicleEvent.size());

                    VehicleGpsMessageDTO event = vehicleEvent.get(0);

                    assertEquals(travel.getId().toString(), event.travelId());
                    assertEquals(city.getId().toString(), event.city());
                    assertEquals(requestDTO.latitude(), event.vehicleLocation().latitude());
                    assertEquals(requestDTO.longitude(), event.vehicleLocation().longitude());
                    assertEquals(requestDTO.speed(), event.vehicleLocation().speed());
                    assertEquals(requestDTO.heading(), event.vehicleLocation().heading());
                }

                @Test
                @DisplayName("Deve realizar a publicação dos três eventos corretamente, sem nenhuma interferência ou publicação extra")
                void shouldPublishExactlyThreeDomainEvents() throws Exception {
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                    // getLiveLocation
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                    redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                    redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                    redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    // NewLocationReceivedEvents
                    List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(1, receivedEvents.size());
                    NewLocationReceivedEvents event = receivedEvents.get(0);

                    assertEquals(travel.getId(), event.travelId());
                    assertEquals(requestDTO.latitude(), event.latitude());
                    assertEquals(requestDTO.longitude(), event.longitude());
                    assertEquals(requestDTO.speed(), event.speed());
                    assertEquals(requestDTO.heading(), event.heading());
                    assertEquals(TravelStatus.TRAVELLING, event.status());

                    assertNotNull(event.timestamp());

                    // StudentAwayStateCheckEvent
                    List<StudentAwayStateCheckEvent> studentAwayState = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(1, studentAwayState.size());
                    StudentAwayStateCheckEvent eventTwo = studentAwayState.getFirst();

                    assertEquals(travel.getId(), eventTwo.travelId());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"), eventTwo.liveLocationDTO().lastCalcLat().toString());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"), eventTwo.liveLocationDTO().lastCalcLng().toString());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"), eventTwo.liveLocationDTO().geometry());
                    assertEquals(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"), eventTwo.liveLocationDTO().distance().toString());

                    assertEquals(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"), eventTwo.liveLocationDTO().latitude().toString());
                    assertEquals(redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"), eventTwo.liveLocationDTO().longitude().toString());

                    // VehicleGpsMessageDTO
                    List<VehicleGpsMessageDTO> vehicleEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(1, vehicleEvent.size());
                    VehicleGpsMessageDTO thirdEvent = vehicleEvent.get(0);

                    assertEquals(travel.getId().toString(), thirdEvent.travelId());
                    assertEquals(city.getId().toString(), thirdEvent.city());
                    assertEquals(requestDTO.latitude(), thirdEvent.vehicleLocation().latitude());
                    assertEquals(requestDTO.longitude(), thirdEvent.vehicleLocation().longitude());
                    assertEquals(requestDTO.speed(), thirdEvent.vehicleLocation().speed());
                    assertEquals(requestDTO.heading(), thirdEvent.vehicleLocation().heading());
                }
            }

            @Nested
            class failureScenarios {

                @Test
                @DisplayName("Deve não publicar nenhum dos eventos quando o body possuir um travelId diferente da URL")
                void shouldNotPublishEventsWhenTravelIdsAreDifferent() throws Exception {
                    mockMvc.perform(post(DEFAULT_CONTROLLER_PATH + "/travels/" + UUID.randomUUID() + "/locations/" + city.getId())
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isBadRequest());

                    List<NewLocationReceivedEvents> newLocEvent = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(0, newLocEvent.size());

                    List<StudentAwayStateCheckEvent> studentAwayEvent = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(0, studentAwayEvent.size());

                    List<VehicleGpsMessageDTO> vehicleGpsEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(0, vehicleGpsEvent.size());
                }

                @ParameterizedTest
                @DisplayName("Não deve realizar a publicação do evento quando as coordenadas do veículo forem inválidas")
                @MethodSource("invalidVehicleLocationProvider")
                void shouldNotPublishEventsWhenCoordinatesAreInvalid(VehicleLocationRequestDTO invalidVehicleLocDTO) throws Exception {
                    mockMvc.perform(post(completePathController)
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(invalidVehicleLocDTO)))
                            .andDo(print())
                            .andExpect(status().isBadRequest());

                    List<NewLocationReceivedEvents> newLocEvent = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(0, newLocEvent.size());

                    List<StudentAwayStateCheckEvent> studentAwayEvent = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(0, studentAwayEvent.size());

                    List<VehicleGpsMessageDTO> vehicleGpsEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(0, vehicleGpsEvent.size());
                }

                public static Stream<Arguments> invalidVehicleLocationProvider() {
                    return Stream.of(
                            Arguments.of(new VehicleLocationRequestDTO(null, -12.9750, -38.5020, 60.0, 180.0)),
                            Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.5020, 60.0, 180.0)),
                            Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, null, 60.0, 180.0)),
                            Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, null, 180.0)),
                            Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, -38.5020, 60.0, null))
                    );
                }

                @ParameterizedTest
                @DisplayName("Não deve publicar os eventos quando a viagem não estiver em andamento")
                @MethodSource("invalidTravelStatusProvider")
                void shouldNotPublishEventsWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) throws Exception {
                    TravelCacheDTO withInvalidStatus = new TravelCacheDTO(travel.getId(), invalidTravelStatus, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());
                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(withInvalidStatus);
                    
                    mockMvc.perform(post(completePathController)
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isConflict());

                    List<NewLocationReceivedEvents> newLocEvent = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(0, newLocEvent.size());

                    List<StudentAwayStateCheckEvent> studentAwayEvent = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(0, studentAwayEvent.size());

                    List<VehicleGpsMessageDTO> vehicleGpsEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(0, vehicleGpsEvent.size());
                }

                public static Stream<Arguments> invalidTravelStatusProvider() {
                    return Stream.of(
                            Arguments.of(TravelStatus.PENDING),
                            Arguments.of(TravelStatus.CANCELED),
                            Arguments.of(TravelStatus.FINISH)
                    );
                }

                @ParameterizedTest
                @DisplayName("Não deve publicar quando o Mapbox falhar no primeiro cálculo de rota")
                @MethodSource("invalidRouteDetailsDTO")
                void shouldNotPublishEventsWhenInitialRouteCalculationFails(RouteDetailsDTO invalidRouteDetailsDTO) throws Exception {
                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(invalidRouteDetailsDTO);

                    mockMvc.perform(post(completePathController)
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isBadGateway());

                    List<NewLocationReceivedEvents> newLocEvent = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(0, newLocEvent.size());

                    List<StudentAwayStateCheckEvent> studentAwayEvent = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(0, studentAwayEvent.size());

                    List<VehicleGpsMessageDTO> vehicleGpsEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(0, vehicleGpsEvent.size());
                }

                public static Stream<Arguments> invalidRouteDetailsDTO() {
                    return Stream.of(
                            Arguments.of(new RouteDetailsDTO(120.0, null, "encoded_geometry_polyline")),
                            Arguments.of(new RouteDetailsDTO(120.0, 5000.0, null)),
                            Arguments.of((RouteDetailsDTO) null)
                    );
                }

                @ParameterizedTest
                @DisplayName("Deve não publicar nenhum evento quando o mapbox falhar no récalculo de rota")
                @MethodSource("invalidRouteDetailsDTO")
                void shouldNotPublishEventsWhenRouteRecalculationFails(RouteDetailsDTO invalidRouteDetailsDTO) throws Exception {
                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenReturn(60.0);
                    when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(routeDeviationDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(invalidRouteDetailsDTO);

                    mockMvc.perform(post(completePathController)
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isBadGateway());

                    List<NewLocationReceivedEvents> newLocEvent = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                    assertEquals(0, newLocEvent.size());

                    List<StudentAwayStateCheckEvent> studentAwayEvent = applicationEvents.stream(StudentAwayStateCheckEvent.class).toList();
                    assertEquals(0, studentAwayEvent.size());

                    List<VehicleGpsMessageDTO> vehicleGpsEvent = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                    assertEquals(0, vehicleGpsEvent.size());
                }
            }
        }

        @Nested
        @DisplayName("Valida as regras de negócio do método async do algoritmo de auto desvinculo do estudante")
        class asyncStudentAwayState {

           @Nested
           class successScenarios {

               @Test
               @DisplayName("Não deve processar quando não houver estudantes vinculados à viagem")
               void shouldIgnoreProcessingWhenNoStudentsAreLinkedToTravel() throws Exception {
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                   // getLiveLocation
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                   redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                   redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verifyNoInteractions(trackingNotificationService);

                               assertEquals(StudentTravelStatus.ACTIVE, studentTravel.getStudentTravelStatus());

                               // confirma que a key se quer foi criada
                               assertTrue(redisTemplate.opsForHash()
                                               .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                               .isEmpty()
                               );
                           });
               }

               @Test
               @DisplayName("Deve manter o estudante como ACTIVE quando permanecer dentro da distância permitida")
               void shouldKeepStudentActiveWhenDistanceIsWithinThreshold() throws Exception {
                   studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                   position = studentTravel.getPosition();

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(30.0); // distance dentro do permitido

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               StudentTravel updatedStudentTravel = studentTravelRepository.findAll().getFirst();

                               // assertions pro disconnectedStudentFromTrip
                               assertNotEquals(StudentTravelStatus.AUTO_DISCONNECTED, updatedStudentTravel.getStudentTravelStatus());
                               assertEquals(StudentTravelStatus.ACTIVE, updatedStudentTravel.getStudentTravelStatus());
                               assertNull(updatedStudentTravel.getDisembarkHour());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

                @Test
                @DisplayName("Deve alterar o estudante para AWAY_FROM_BUS ao ultrapassar distância mínima")
                void shouldMarkStudentAsAwayWhenDistanceExceedsThreshold() throws Exception {
                    studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                    position = studentTravel.getPosition();

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenReturn(400.0); // distance dentro do permitido

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    Awaitility.await()
                            .atMost(5, TimeUnit.SECONDS)
                            .untilAsserted(() -> {
                                StudentTravel updated = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                                // assertions pro disconnectedStudentFromTrip
                                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updated.getStudentTravelStatus());
                                assertNull(updated.getDisembarkHour());
                                assertTrue(updated.isEmbark());

                                assertNull(updated.getDisembarkHour());

                                // o timestamp deve ter sido gravado no redis
                                Object redisValue = redisTemplate.opsForHash().get(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString());
                                assertNotNull(redisValue);

                                // garante que a haja apenas uma key gravada, e que a key (do redis) seja a do estudante
                                Map<Object, Object> awayStudents = redisTemplate.opsForHash().entries(STUDENT_TRAVEL_KEY_PREFIX);
                                assertEquals(1, awayStudents.size());

                                assertTrue(awayStudents.containsKey(student.getId().toString()));

                                verifyNoInteractions(trackingNotificationService);
                            });
                }

                @Test
                @DisplayName("Deve manter o estudante com status de AWAY enquanto o tempo mínimo de desconexão ainda não for atingido ")
                void shouldKeepStudentAwayWhenAutoDisconnectTimeWasNotReached() throws Exception {
                    studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                    position = studentTravel.getPosition();

                    // escrita do timestamp como 2m
                    long twoMinutes = Instant.now().plusSeconds(120).toEpochMilli();
                    redisTemplate.opsForHash().put(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString(), String.valueOf(twoMinutes));

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenReturn(410.0); // distance fora do permitido

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    Awaitility.await()
                            .atMost(5, TimeUnit.SECONDS)
                            .untilAsserted(() -> {
                                StudentTravel updated = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                                // assertions pro disconnectedStudentFromTrip
                                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updated.getStudentTravelStatus());
                                assertNull(updated.getDisembarkHour());
                                assertTrue(updated.isEmbark());

                                assertNull(updated.getDisembarkHour());

                                // o timestamp deve permanecer no redis
                                Object redisValue = redisTemplate.opsForHash().get(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString());
                                assertNotNull(redisValue);

                                // garante que a haja apenas uma key gravada, e que a key (do redis) seja a do estudante
                                Map<Object, Object> awayStudents = redisTemplate.opsForHash().entries(STUDENT_TRAVEL_KEY_PREFIX);
                                assertEquals(1, awayStudents.size());
                                assertTrue(awayStudents.containsKey(student.getId().toString()));

                                // não manda notificação alguma
                                verifyNoInteractions(trackingNotificationService);
                            });
                }

                @Test
                @DisplayName("Deve auto desconectar o estudante após exceder o tempo limite e distância permanecer acima da permitida")
                void shouldAutoDisconnectStudentWhenDistanceAndTimeThresholdAreReached() throws Exception {
                    studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                    position = studentTravel.getPosition();

                    // escrita do timestamp como 6m (acima do tempo limite)
                    long sixMinutes = Instant.now().minus(Duration.ofMinutes(6)).toEpochMilli();
                    redisTemplate.opsForHash().put(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString(), String.valueOf(sixMinutes));

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenReturn(450.0); // distance fora do permitido

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    Awaitility.await()
                            .atMost(5, TimeUnit.SECONDS)
                            .untilAsserted(() -> {
                                StudentTravel updated = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                                // assertions pro disconnectedStudentFromTrip
                                assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, updated.getStudentTravelStatus());
                                assertNotNull(updated.getDisembarkHour());
                                assertFalse(updated.isEmbark());

                                // o studentId deve ser limpo do redis
                                Object redisValue = redisTemplate.opsForHash().get(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString());
                                assertNull(redisValue);

                                // deve garantir que não haja mais o id do estudante (key) que foi auto desconectado
                                Map<Object, Object> awayStudents = redisTemplate.opsForHash().entries(STUDENT_TRAVEL_KEY_PREFIX);
                                assertEquals(0, awayStudents.size());
                                assertFalse(awayStudents.containsKey(student.getId().toString()));

                                // manda notificação de auto desvinculação
                                verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(Travel.class), any());
                            });
                }

                @Test
                @DisplayName("Deve fazer o estudante retornar para ACTIVE quando voltar para próximo do ônibus (distância permitida)")
                void shouldRestoreStudentToActiveWhenReturningNearVehicle() throws Exception {
                    studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                    position = studentTravel.getPosition();

                    // escrita do timestamp como 3m (ainda dentro do limite)
                    long sixMinutes = Instant.now().minus(Duration.ofMinutes(3)).toEpochMilli();
                    redisTemplate.opsForHash().put(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString(), String.valueOf(sixMinutes));

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenReturn(250.0); // distance dentro do permitido

                    mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());


                    Awaitility.await().atMost(5, TimeUnit.SECONDS)
                            .untilAsserted(() -> {
                                StudentTravel updated = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

                                // assertions pro disconnectedStudentFromTrip
                                assertEquals(StudentTravelStatus.ACTIVE, updated.getStudentTravelStatus());
                                assertNull(updated.getDisembarkHour());
                                assertTrue(updated.isEmbark());

                                // o studentId deve ser limpo do redis
                                Object redisValue = redisTemplate.opsForHash().get(STUDENT_TRAVEL_KEY_PREFIX, student.getId().toString());
                                assertNull(redisValue);

                                // deve garantir que não haja mais o id do estudante (key) que foi auto desconectado
                                Map<Object, Object> awayStudents = redisTemplate.opsForHash().entries(STUDENT_TRAVEL_KEY_PREFIX);
                                assertEquals(0, awayStudents.size());
                                assertFalse(awayStudents.containsKey(student.getId().toString()));

                                // não manda notificação
                                verifyNoInteractions(trackingNotificationService );
                            });
                }

                @Test
                @DisplayName("Deve conseguir processar múltiplos estudantes simulteaneamente, com dados divergentes entre eles")
                void shouldProcessMultipleStudentsInSingleExecution() throws Exception {
                    Student student2 = new Student(null, "email2@exemplo.com", "senha123", "studentName2", "studentLastName", "11999999998", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
                    Student student3 = new Student(null, "email3@exemplo.com", "senha123", "studentName3", "studentLastName", "11999999997", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");

                    student2 = studentRepository.saveAndFlush(student2);
                    student3 = studentRepository.saveAndFlush(student3);

                    StudentTravel st2 = new StudentTravel(null, travel, student2, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                    StudentTravel st3 = new StudentTravel(null, travel, student3, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.AWAY_FROM_BUS);

                    studentTravel = createStudentTravelWithPosition(travel, student, -11.732, -38.726);
                    StudentTravel studentTravel2 = createStudentTravelWithPosition(travel, student2, -11.733, -38.726);
                    StudentTravel studentTravel3 = createStudentTravelWithPosition(travel, student3, -11.734, -38.726);

                    String redisHashKey = STUDENT_TRAVEL_KEY_PREFIX;
                    long threeMinutesAgo = Instant.now().minus(Duration.ofMinutes(3)).toEpochMilli();
                    long nineMinutesAgo = Instant.now().minus(Duration.ofMinutes(9)).toEpochMilli();

                    redisTemplate.opsForHash().put(redisHashKey, student2.getId().toString(), String.valueOf(threeMinutesAgo));
                    redisTemplate.opsForHash().put(redisHashKey, student3.getId().toString(), String.valueOf(nineMinutesAgo));

                    when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                    when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                    when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                            .thenAnswer(invocation -> {
                                Double studentLat = invocation.getArgument(2);
                                Double studentLng = invocation.getArgument(3);

                                if (studentLat.equals(studentTravel.getPosition().getLatitude())) {
                                    return 30.0;
                                }
                                if (studentLat.equals(studentTravel2.getPosition().getLatitude())) {
                                    return 350.0;
                                }
                                if (studentLat.equals(studentTravel3.getPosition().getLatitude())) {
                                    return 600.0;
                                }
                                return 0.0;
                            });

                    mockMvc.perform(post(completePathController)
                                    .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(requestDTO)))
                            .andDo(print())
                            .andExpect(status().isOk());

                    Student finalStudent = student2;
                    Student finalStudent1 = student3;
                    Awaitility.await().atMost(5, TimeUnit.SECONDS)
                            .pollInterval(500, TimeUnit.MILLISECONDS)
                            .untilAsserted(() -> {
                                List<StudentTravel> allStudentTravels = studentTravelRepository.findAll();

                                StudentTravel updatedSt1 = findStudentTravelByStudentId(allStudentTravels, student.getId());
                                StudentTravel updatedSt2 = findStudentTravelByStudentId(allStudentTravels, finalStudent.getId());
                                StudentTravel updatedSt3 = findStudentTravelByStudentId(allStudentTravels, finalStudent1.getId());

                                assertEquals(StudentTravelStatus.ACTIVE, updatedSt1.getStudentTravelStatus());
                                assertTrue(updatedSt1.isEmbark());
                                assertNull(updatedSt1.getDisembarkHour());
                                assertNull(redisTemplate.opsForHash().get(redisHashKey, student.getId().toString()));

                                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updatedSt2.getStudentTravelStatus());
                                assertTrue(updatedSt2.isEmbark());
                                assertNull(updatedSt2.getDisembarkHour());
                                // O timestamp DEVE continuar existindo, pois ainda não estourou o tempo
                                assertNotNull(redisTemplate.opsForHash().get(redisHashKey, finalStudent.getId().toString()));

                                assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, updatedSt3.getStudentTravelStatus());
                                assertFalse(updatedSt3.isEmbark());
                                assertNotNull(updatedSt3.getDisembarkHour());
                                // timestamp deve ter sido removido após o desvínculo
                                assertNull(redisTemplate.opsForHash().get(redisHashKey, finalStudent1.getId().toString()));

                                verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(Travel.class), any());
                            });
                }
            }

           @Nested
           class failureScenarios {

               @Test
               void shouldThrowTripNotFoundWhenTravelDoesNotExist() throws Exception {
                   studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                   position = studentTravel.getPosition();

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenThrow(new TripNotFound("Viagem não existe"));

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isNotFound());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // não deve existir key no redis
                               assertTrue(redisTemplate.opsForHash()
                                               .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                               .isEmpty());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               @ParameterizedTest
               @MethodSource("travelStatusProvider")
               void shouldThrowTravelExceptionWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) throws Exception {
                   TravelCacheDTO invalidTravelCacheDTO = new TravelCacheDTO(travel.getId(), invalidTravelStatus, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

                   studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                   position = studentTravel.getPosition();

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(invalidTravelCacheDTO);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isConflict());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // não deve existir key no redis
                               assertTrue(redisTemplate.opsForHash()
                                       .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                       .isEmpty());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               public static Stream<Arguments> travelStatusProvider() {
                   return Stream.of(
                           Arguments.of(TravelStatus.FINISH),
                           Arguments.of(TravelStatus.PENDING),
                           Arguments.of(TravelStatus.CANCELED)
                   );
               }

               @Test
               @DisplayName("Deve ignorar quando o estudante não estiver embarcado")
               void shouldIgnoreStudentWhenEmbarkIsFalse() throws Exception {
                   StudentTravel studentTravelNotEmbarked = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                   studentTravelNotEmbarked = studentTravelRepository.saveAndFlush(studentTravelNotEmbarked);

                   GeoPosition positionNotEmbarked = new GeoPosition(null, -11.732, -38.726, Instant.now(), studentTravelNotEmbarked);
                   positionNotEmbarked = geoPositionRepository.saveAndFlush(positionNotEmbarked);

                   studentTravelNotEmbarked.setPosition(positionNotEmbarked);

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // não deve existir key no redis
                               assertTrue(redisTemplate.opsForHash()
                                       .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                       .isEmpty());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               @ParameterizedTest
               @DisplayName("Deve ignorar quando o estudante estiver com Status LEFT ou AUTO_DISCONNECTED")
               @MethodSource("studentTravelStatusProvider")
               void shouldIgnoreStudentAlreadyMarkedAsLeftOrAutoDisconnected(StudentTravelStatus invalidStudentTravelStatus) throws Exception {
                   StudentTravel studentTravelNotEmbarked = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), null, null, invalidStudentTravelStatus);
                   studentTravelNotEmbarked = studentTravelRepository.saveAndFlush(studentTravelNotEmbarked);

                   GeoPosition positionNotEmbarked = new GeoPosition(null, -11.732, -38.726, Instant.now(), studentTravelNotEmbarked);
                   positionNotEmbarked = geoPositionRepository.saveAndFlush(positionNotEmbarked);

                   studentTravelNotEmbarked.setPosition(positionNotEmbarked);

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // não deve existir key no redis
                               assertTrue(redisTemplate.opsForHash()
                                       .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                       .isEmpty());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               public static Stream<Arguments> studentTravelStatusProvider() {
                   return Stream.of(
                           Arguments.of(StudentTravelStatus.AUTO_DISCONNECTED),
                           Arguments.of(StudentTravelStatus.LEFT)
                   );
               }

               @Test
               @DisplayName("Deve ignorar quando distanceResponse não retornar nenhum estudante (aplicando filtros de posição e distância)")
               void shouldIgnoreDistanceEntryWithoutMatchingStudent() throws Exception {
                   StudentTravel studentTravelLeft = new StudentTravel(
                           null,
                           travel,
                           student,
                           true,
                           Instant.now().minusSeconds(20),
                           Instant.now(),
                           null,
                           StudentTravelStatus.LEFT
                   );
                   studentTravelLeft = studentTravelRepository.saveAndFlush(studentTravelLeft);

                   GeoPosition positionLeft = new GeoPosition(
                           null,
                           -11.732,
                           -38.726,
                           Instant.now(),
                           studentTravelLeft
                   );
                   positionLeft = geoPositionRepository.saveAndFlush(positionLeft);

                   studentTravelLeft.setPosition(positionLeft);

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // não deve existir key no redis
                               assertTrue(redisTemplate.opsForHash()
                                       .entries(STUDENT_TRAVEL_KEY_PREFIX + travel.getId())
                                       .isEmpty());

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               @Test
               @DisplayName("Deve criar timestamp no Redis e marcar como AWAY_FROM_BUS quando não houver estado prévio")
               void shouldCreateAwayTimestampWhenRedisDoesNotContainPreviousState() throws Exception {
                   StudentTravel studentTravelActive = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                   studentTravelActive = studentTravelRepository.saveAndFlush(studentTravelActive);
                   
                   GeoPosition positionActive = new GeoPosition(null, -11.732, -38.726, Instant.now(), studentTravelActive);
                   positionActive = geoPositionRepository.saveAndFlush(positionActive);
                   studentTravelActive.setPosition(positionActive);
                   
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                   
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(600.0);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   StudentTravel finalStudentTravelActive = studentTravelActive;
                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .pollInterval(500, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {

                               StudentTravel updated = studentTravelRepository.findById(finalStudentTravelActive.getId()).orElseThrow();
                               assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updated.getStudentTravelStatus(),
                                       "O status deve ser atualizado para AWAY_FROM_BUS na primeira detecção de distância");

                               assertTrue(updated.isEmbark(), "O estudante deve continuar embarcado (embark=true)");
                               assertNull(updated.getDisembarkHour(), "A hora de desembarque deve permanecer nula neste estágio");

                               verifyNoInteractions(trackingNotificationService);
                           });
               }

               @Test
               @DisplayName("Não deve enviar nenhuma notificação quando não houver nenhuma desconexão")
               void shouldNotSendNotificationsWhenNoAutoDisconnectOccurs() throws Exception {
                   studentTravel = createStudentTravelWithPosition(travel, student, null, null);
                   position = studentTravel.getPosition();

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(30.0); // distance dentro do permitido

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               // nao envia notificação
                               verifyNoInteractions(trackingNotificationService);
                           });
               }
           }

           @Nested
           @DisplayName("Garante que o JPA e o Redis estão devidamente sincronizados")
           class RedisAndDatabaseIntegration {

               @Test
               @DisplayName("Deve limpar timestamp apenas dos estudantes processados")
               void shouldRemoveAwayTimestampOnlyForProcessedStudents() throws Exception {
                   Student student2 = new Student(null, "email2@exemplo.com", "senha123", "studentName2", "studentLastName", "11999999998", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
                   Student student3 = new Student(null, "email3@exemplo.com", "senha123", "studentName3", "studentLastName", "11999999997", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");

                   student2 = studentRepository.saveAndFlush(student2);
                   student3 = studentRepository.saveAndFlush(student3);

                   StudentTravel st2 = new StudentTravel(null, travel, student2, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                   StudentTravel st3 = new StudentTravel(null, travel, student3, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.AWAY_FROM_BUS);

                   studentTravel = createStudentTravelWithPosition(travel, student, -11.732, -38.726);
                   StudentTravel studentTravel2 = createStudentTravelWithPosition(travel, student2, -11.733, -38.726);
                   StudentTravel studentTravel3 = createStudentTravelWithPosition(travel, student3, -11.734, -38.726);

                   String redisHashKey = STUDENT_TRAVEL_KEY_PREFIX;
                   long oneMinutesAgo = Instant.now().minus(Duration.ofMinutes(1)).toEpochMilli();
                   long threeMinutesAgo = Instant.now().minus(Duration.ofMinutes(3)).toEpochMilli();
                   long nineMinutesAgo = Instant.now().minus(Duration.ofMinutes(9)).toEpochMilli();

                   redisTemplate.opsForHash().put(redisHashKey, student.getId().toString(), String.valueOf(oneMinutesAgo));
                   redisTemplate.opsForHash().put(redisHashKey, student2.getId().toString(), String.valueOf(threeMinutesAgo));
                   redisTemplate.opsForHash().put(redisHashKey, student3.getId().toString(), String.valueOf(nineMinutesAgo));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenAnswer(invocation -> {
                               Double studentLat = invocation.getArgument(2);
                               Double studentLng = invocation.getArgument(3);

                               if (studentLat.equals(studentTravel.getPosition().getLatitude())) {
                                   return 30.0;
                               }
                               if (studentLat.equals(studentTravel2.getPosition().getLatitude())) {
                                   return 350.0;
                               }
                               if (studentLat.equals(studentTravel3.getPosition().getLatitude())) {
                                   return 600.0;
                               }
                               return 0.0;
                           });

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Student finalStudent = student2;
                   Student finalStudent1 = student3;
                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .pollInterval(500, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               List<StudentTravel> allStudentTravels = studentTravelRepository.findAll();

                               StudentTravel updatedSt1 = findStudentTravelByStudentId(allStudentTravels, student.getId());
                               StudentTravel updatedSt2 = findStudentTravelByStudentId(allStudentTravels, finalStudent.getId());
                               StudentTravel updatedSt3 = findStudentTravelByStudentId(allStudentTravels, finalStudent1.getId());

                               assertEquals(StudentTravelStatus.ACTIVE, updatedSt1.getStudentTravelStatus());
                               assertTrue(updatedSt1.isEmbark());
                               assertNull(updatedSt1.getDisembarkHour());
                               assertNull(redisTemplate.opsForHash().get(redisHashKey, student.getId().toString()));

                               assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updatedSt2.getStudentTravelStatus());
                               assertTrue(updatedSt2.isEmbark());
                               assertNull(updatedSt2.getDisembarkHour());
                               // O timestamp DEVE continuar existindo, pois ainda não estourou o tempo
                               assertNotNull(redisTemplate.opsForHash().get(redisHashKey, finalStudent.getId().toString()));

                               assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, updatedSt3.getStudentTravelStatus());
                               assertFalse(updatedSt3.isEmbark());
                               assertNotNull(updatedSt3.getDisembarkHour());
                               // timestamp deve ter sido removido após o desvínculo
                               assertNull(redisTemplate.opsForHash().get(redisHashKey, finalStudent1.getId().toString()));

                               verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(Travel.class), any());
                           });
               }

               @Test
               @DisplayName("Deve atualizar status em lote corretamente (ACTIVE, AWAY e AUTO_DISCONNECTED na mesma execução)")
               void shouldPersistBatchStatusUpdatesCorrectly() throws Exception {
                   Student studentA = new Student(null, "emailA@exemplo.com", "senha123", "NameA", "LastNameA", "11999999991", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia");
                   Student studentB = new Student(null, "emailB@exemplo.com", "senha123", "NameB", "LastNameB", "11999999992", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia");
                   Student studentC = new Student(null, "emailC@exemplo.com", "senha123", "NameC", "LastNameC", "11999999993", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia");

                   studentA = studentRepository.saveAndFlush(studentA);
                   studentB = studentRepository.saveAndFlush(studentB);
                   studentC = studentRepository.saveAndFlush(studentC);

                   StudentTravel stA = createStudentTravelWithPosition(travel, studentA, -11.732, -38.726);
                   stA.setStudentTravelStatus(StudentTravelStatus.ACTIVE);
                   stA = studentTravelRepository.saveAndFlush(stA);

                   StudentTravel stB = createStudentTravelWithPosition(travel, studentB, -11.733, -38.726);
                   stB.setStudentTravelStatus(StudentTravelStatus.ACTIVE);
                   stB = studentTravelRepository.saveAndFlush(stB);

                   StudentTravel stC = createStudentTravelWithPosition(travel, studentC, -11.734, -38.726);
                   stC.setStudentTravelStatus(StudentTravelStatus.AWAY_FROM_BUS);
                   stC = studentTravelRepository.saveAndFlush(stC);

                   String redisHashKey = STUDENT_TRAVEL_KEY_PREFIX;

                   redisTemplate.opsForHash().put(redisHashKey, studentA.getId().toString(), String.valueOf(Instant.now().minus(Duration.ofMinutes(5)).toEpochMilli()));
                   // Student B: Sem timestamp propositalmente
                   redisTemplate.opsForHash().put(redisHashKey, studentC.getId().toString(), String.valueOf(Instant.now().minus(Duration.ofMinutes(9)).toEpochMilli()));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   StudentTravel finalStA = stA;
                   StudentTravel finalStB = stB;
                   StudentTravel finalStC = stC;
                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenAnswer(invocation -> {
                               Double studentLat = invocation.getArgument(2);
                               if (studentLat.equals(finalStA.getPosition().getLatitude())) return 30.0;   // Perto
                               if (studentLat.equals(finalStB.getPosition().getLatitude())) return 390.0;  // Longe, tempo ok
                               if (studentLat.equals(finalStC.getPosition().getLatitude())) return 600.0;  // Longe, tempo estourado
                               return 0.0;
                           });

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Student finalStudentA = studentA;
                   Student finalStudentB = studentB;
                   Student finalStudentC = studentC;
                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .pollInterval(500, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               List<StudentTravel> allStudentTravels = studentTravelRepository.findAll();

                               StudentTravel updatedStA = findStudentTravelByStudentId(allStudentTravels, finalStudentA.getId());
                               StudentTravel updatedStB = findStudentTravelByStudentId(allStudentTravels, finalStudentB.getId());
                               StudentTravel updatedStC = findStudentTravelByStudentId(allStudentTravels, finalStudentC.getId());

                               // estudante A: continua ACTIVE e redis deve ser limpo
                               assertEquals(StudentTravelStatus.ACTIVE, updatedStA.getStudentTravelStatus());
                               assertTrue(updatedStA.isEmbark());
                               assertNull(updatedStA.getDisembarkHour());
                               assertNull(redisTemplate.opsForHash().get(redisHashKey, finalStudentA.getId().toString()), "Redis de A deve ser limpo ao ficar perto");

                               // estudante B: vai de ACTIVE para AWAY e deve ter um novo timestamp no redis
                               assertEquals(StudentTravelStatus.AWAY_FROM_BUS, updatedStB.getStudentTravelStatus());
                               assertTrue(updatedStB.isEmbark());
                               assertNull(updatedStB.getDisembarkHour());
                               assertNotNull(redisTemplate.opsForHash().get(redisHashKey, finalStudentB.getId().toString()), "Redis de B deve ter recebido timestamp");

                               // estudante C: vai de AWAY para AUTO_DISCONNECTED, e o redis deve ser limpo
                               assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, updatedStC.getStudentTravelStatus());
                               assertFalse(updatedStC.isEmbark());
                               assertNotNull(updatedStC.getDisembarkHour()); // deve ser não-null apenas para o estudante desembarcado
                               assertNull(redisTemplate.opsForHash().get(redisHashKey, finalStudentC.getId().toString()), "Redis de C deve ser limpo após desconexão");

                               // envia somente uma notificação
                               verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(Travel.class), any());
                           });
               }

               @Test
               @DisplayName("Deve limpar o redis após o estudante for auto desconectado")
               void shouldRemoveStudentRedisStateAfterAutoDisconnect() throws Exception {
                   Student studentC = new Student(null, "emailC@exemplo.com", "senha123", "NameC", "LastNameC", "11999999993", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia");

                   studentC = studentRepository.saveAndFlush(studentC);

                   StudentTravel stC = createStudentTravelWithPosition(travel, studentC, -11.734, -38.726);
                   stC.setStudentTravelStatus(StudentTravelStatus.AWAY_FROM_BUS);
                   stC = studentTravelRepository.saveAndFlush(stC);

                   String redisHashKey = STUDENT_TRAVEL_KEY_PREFIX;

                   redisTemplate.opsForHash().put(redisHashKey, studentC.getId().toString(), String.valueOf(Instant.now().minus(Duration.ofMinutes(9)).toEpochMilli()));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                           .thenReturn(600.0);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Student finalStudentC = studentC;
                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .pollInterval(500, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               List<StudentTravel> allStudentTravels = studentTravelRepository.findAll();

                               StudentTravel updatedStC = findStudentTravelByStudentId(allStudentTravels, finalStudentC.getId());

                               // estudante C: vai de AWAY para AUTO_DISCONNECTED, e o redis deve ser limpo
                               assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, updatedStC.getStudentTravelStatus());
                               assertFalse(updatedStC.isEmbark());
                               assertNotNull(updatedStC.getDisembarkHour()); // deve ser não-null apenas para o estudante desembarcado

                               // deve limpar o redis
                               assertNull(redisTemplate.opsForHash().get(redisHashKey, finalStudentC.getId().toString()), "Redis de C deve ser limpo após desconexão");

                               // envia somente uma notificação
                               verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(Travel.class), any());
                           });
               }
           }

            // Método auxiliar para evitar findAll().get(index)
            private StudentTravel findStudentTravelByStudentId(List<StudentTravel> travels, UUID studentId) {
                return travels.stream()
                        .filter(st -> st.getStudent().getId().equals(studentId))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("StudentTravel não encontrado para o studentId: " + studentId));
            }

            /*
             * Método dedicado para criar e persistir a relação completa:
             * Travel -> StudentTravel <-> GeoPosition (e vinculando ao Student)
             */
            private StudentTravel createStudentTravelWithPosition(Travel travel, Student student, Double latitude, Double longitude) {
                StudentTravel st = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                st = studentTravelRepository.saveAndFlush(st);

                if (latitude == null) latitude = -11.732;
                if (longitude == null) longitude = -38.726;

                GeoPosition position = new GeoPosition(null, latitude, longitude, Instant.now(), st);
                position = geoPositionRepository.saveAndFlush(position);

                st.setPosition(position);

                if (travel.getStudentTravels() == null) {
                    travel.setStudentTravels(new HashSet<>());
                }
                travel.getStudentTravels().add(st);

                return st;
            }
        }

        @Nested
        @DisplayName("Valida apenas o comportamento padrão do listener em si, sem entrar em cenários async ou de outros métodos")
        class handleLocationProcessing {

           @Nested
           class successScenarios {

               @Test
               @DisplayName("Deve garantir que um NewLocationReceivedEvents válido seja processado corretamente pelo listener")
               void shouldProcessLocationEventSuccessfully() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();

                   assertEquals(1, receivedEvents.size());

                   NewLocationReceivedEvents event = receivedEvents.get(0);
                   assertEquals(travel.getId(), event.travelId());
                   assertEquals(requestDTO.latitude(), event.latitude());
                   assertEquals(requestDTO.longitude(), event.longitude());
                   assertEquals(requestDTO.speed(), event.speed());
                   assertEquals(requestDTO.heading(), event.heading());
                   assertEquals(TravelStatus.TRAVELLING, event.status());
                   assertNotNull(event.timestamp());

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               // verifica se os serviços externos foram chamados corretamente após o processamento do evento
                               verify(travelTrackingService, times(1)).processNewLocation(any(VehicleLocationRequestDTO.class));

                               verify(pushNotificationService, times(1)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                               verify(pushNotificationService, times(1)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           });
               }

               @Test
               @DisplayName("Deve continuar o fluxo de notificação mesmo quando processNewLocation lançar EtaDataStatesInvalidException")
               void shouldContinueNotificationFlowWhenEtaProcessingFails() throws Exception {
                   doThrow(new EtaDataStatesInvalidException("Dados do previousEta inválidos ou null"))
                           .when(travelTrackingService)
                           .processNewLocation(any(VehicleLocationRequestDTO.class));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               // verificação de chamadas dos demais métodos
                               verify(travelTrackingService, times(1)).processNewLocation(any(VehicleLocationRequestDTO.class));

                               verify(pushNotificationService, times(1)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));
                               verify(pushNotificationService, times(1)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           });
               }

               @Test
               @DisplayName("Deve realizar a conversão do evento para VehicleLocationRequestDTO")
               void shouldConvertDomainEventIntoVehicleLocationRequest() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                   assertEquals(1, receivedEvents.size());

                   NewLocationReceivedEvents originalEvent = receivedEvents.get(0);

                   ArgumentCaptor<VehicleLocationRequestDTO> dtoCaptor = ArgumentCaptor.forClass(VehicleLocationRequestDTO.class);

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {

                               verify(travelTrackingService, times(1)).processNewLocation(dtoCaptor.capture());

                               VehicleLocationRequestDTO capturedDto = dtoCaptor.getValue();

                               assertEquals(originalEvent.travelId(), capturedDto.travelId());
                               assertEquals(originalEvent.latitude(), capturedDto.latitude());
                               assertEquals(originalEvent.longitude(), capturedDto.longitude());
                               assertEquals(originalEvent.speed(), capturedDto.speed());
                               assertEquals(originalEvent.heading(), capturedDto.heading());
                           });
               }

               @Test
               @DisplayName("Deve garantir a execução dos serviços de notificação exatamente uma vez cada um")
               void shouldExecuteNotificationServicesAfterLocationProcessing() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();

                   assertEquals(1, receivedEvents.size());

                   NewLocationReceivedEvents event = receivedEvents.get(0);
                   assertEquals(travel.getId(), event.travelId());
                   assertEquals(requestDTO.latitude(), event.latitude());
                   assertEquals(requestDTO.longitude(), event.longitude());
                   assertEquals(requestDTO.speed(), event.speed());
                   assertEquals(requestDTO.heading(), event.heading());
                   assertEquals(TravelStatus.TRAVELLING, event.status());
                   assertNotNull(event.timestamp());

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               // verifica se os serviços externos de notificação foram chamados corretamente após o processamento do evento
                               verify(pushNotificationService, times(1)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                               verify(pushNotificationService, times(1)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           });
               }

               @Test
               @DisplayName("Deve garantir que os serviços externos estão usando o mesmo DTO")
               void shouldReuseSameVehicleLocationDTOForNotificationServices() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<NewLocationReceivedEvents> receivedEvents = applicationEvents.stream(NewLocationReceivedEvents.class).toList();
                   assertEquals(1, receivedEvents.size());

                   NewLocationReceivedEvents originalEvent = receivedEvents.get(0);

                   ArgumentCaptor<VehicleLocationRequestDTO> dtoCaptor = ArgumentCaptor.forClass(VehicleLocationRequestDTO.class);

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {

                               verify(travelTrackingService, times(1)).processNewLocation(dtoCaptor.capture());

                               VehicleLocationRequestDTO capturedDto = dtoCaptor.getValue();

                               assertEquals(originalEvent.travelId(), capturedDto.travelId());
                               assertEquals(originalEvent.latitude(), capturedDto.latitude());
                               assertEquals(originalEvent.longitude(), capturedDto.longitude());
                               assertEquals(originalEvent.speed(), capturedDto.speed());
                               assertEquals(originalEvent.heading(), capturedDto.heading());
                           });
               }

               @Test
               @DisplayName("Deve processar múltiplos eventos de localização consecutivos sem falhas")
               void shouldProcessMultipleLocationEventsSequentially() throws Exception {
                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andExpect(status().isOk());

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andExpect(status().isOk());

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .pollInterval(300, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {
                               // valida se as três requisições foram feitas
                               verify(travelTrackingService, times(3)).processNewLocation(any(VehicleLocationRequestDTO.class));
                               verify(pushNotificationService, times(3)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));
                               verify(pushNotificationService, times(3)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           });
               }
           }
           
           @Nested
           class failureScenarios {

               @Test
               @DisplayName("Deve validar que a exception lançada em ProcessNewLocation não propague")
               void shouldNotPropagateEtaProcessingException() throws Exception {
                   doThrow(new EtaDataStatesInvalidException("Dados do previousEta inválidos ou null"))
                           .when(travelTrackingService)
                           .processNewLocation(any(VehicleLocationRequestDTO.class));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(3, TimeUnit.SECONDS)
                           .pollInterval(200, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> assertDoesNotThrow(() -> {
                               // verificação de chamadas dos demais métodos
                               verify(travelTrackingService, times(1)).processNewLocation(any(VehicleLocationRequestDTO.class));

                               verify(pushNotificationService, times(1)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));
                               verify(pushNotificationService, times(1)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           }));
               }

               @Test
               @DisplayName("Deve acionar o fallback e continuar o fluxo quando checkProximityAlerts falhar persistentemente")
               void shouldPropagateExceptionWhenCheckProximityAlertsFails() throws Exception {
                   RuntimeException simulatedFailure = new RuntimeException("Falha simulada de infraestrutura (ex: Redis indisponível)");
                   doThrow(simulatedFailure)
                           .when(pushNotificationService)
                           .checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                   when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                   when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                   mockMvc.perform(post(completePathController)
                                   .with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   Awaitility.await()
                           .atMost(5, TimeUnit.SECONDS)
                           .pollInterval(500, TimeUnit.MILLISECONDS)
                           .untilAsserted(() -> {

                               // metodo foi chamado e retryado até esgotar as tentativas (3 vezes)
                               verify(pushNotificationService, times(3)).checkProximityAlerts(any(VehicleLocationRequestDTO.class));

                               // metodo de recuperação @Recover foi acionado exatamente 1 vez
                               verify(pushNotificationService, times(1))
                                       .recoverCheckProximityAlerts(eq(simulatedFailure), any(VehicleLocationRequestDTO.class));

                               // o listener NÃO quebrou. O próximo metodo foi chamado normalmente
                               verify(pushNotificationService, times(1)).processVehicleMovement(any(VehicleLocationRequestDTO.class));
                           });
               }
           }

        }

        @Nested
        @DisplayName("Responsável por validar o fluxo completo iniciado pelo markDriverCheckpoint para envio dos dados GPS ao Listener async")
        class handleVehicleGps {

            private void setupCommonMocksFromEventPublishing() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", String.valueOf(-11.232));
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", String.valueOf(-38.001));

                // getLiveLocation
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_teste");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "920.3");
                redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-17.039");
                redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-43.222");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

            }

           @Nested
           class successScenarios {

               @Test
               @DisplayName("Deve enviar GPS ao RabbitMQ após receber checkpoint válido")
               void shouldSendVehicleGpsToRabbitMqAfterValidCheckpoint() throws Exception {
                   setupCommonMocksFromEventPublishing();

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<VehicleGpsMessageDTO> vehicleGpsMessageDTO = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();

                   assertEquals(1, vehicleGpsMessageDTO.size());

                   VehicleGpsMessageDTO vehicleGpsEventPublishing = vehicleGpsMessageDTO.getFirst();
                   assertNotNull(vehicleGpsEventPublishing.travelId());
                   assertNotNull(vehicleGpsEventPublishing.city());
                   assertNotNull(vehicleGpsEventPublishing.vehicleLocation());

                   String expectedRoutingKey = "v1.gps." + vehicleGpsEventPublishing.city() + "." + vehicleGpsEventPublishing.travelId();
                   String expectedExchangeGpsName = RabbitMQConfig.EXCHANGE_GPS_NAME;

                   ArgumentCaptor<GpsPayload> GpsPayloadArgCaptor = ArgumentCaptor.forClass(GpsPayload.class);

                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(VehicleGpsMessageDTO.class));

                               verify(rabbitTemplate, times(1))
                                       .convertAndSend(eq(expectedExchangeGpsName),
                                               eq(expectedRoutingKey),
                                               GpsPayloadArgCaptor.capture(),
                                               argThat((MessagePostProcessor mpp)  -> {
                                                   // simula comportamento do MessageProperties
                                                   Message mockMessage = mock(Message.class);
                                                   MessageProperties mockMessageProperties =  mock(MessageProperties.class);
                                                   when(mockMessage.getMessageProperties()).thenReturn(mockMessageProperties);

                                                   // executa processador real
                                                   mpp.postProcessMessage(mockMessage);

                                                   verify(mockMessageProperties).setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);

                                                   return true;
                                               }));

                           });

                   GpsPayload capturedPayload = GpsPayloadArgCaptor.getValue();
                   assertNotNull(capturedPayload);

                   assertEquals(requestDTO.latitude(), capturedPayload.latitude());
                   assertEquals(requestDTO.longitude(), capturedPayload.longitude());
                   assertEquals(requestDTO.speed(), capturedPayload.speed());
                   assertEquals(requestDTO.heading(), capturedPayload.heading());
                   assertEquals(UUID.fromString(vehicleGpsEventPublishing.city()), capturedPayload.cityId());
                   assertEquals(vehicleGpsEventPublishing.travelId(), capturedPayload.travelId().toString());
                   assertNotNull(capturedPayload.timestamp());

               }

               @Test
               @DisplayName("Deve garantir que a routingKey seja corretamente criada durante a execução do serviço")
               void shouldBuildCorrectGpsRoutingKey() throws Exception {
                   setupCommonMocksFromEventPublishing();

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<VehicleGpsMessageDTO> vehicleGpsMessageDTO = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();
                   assertEquals(1, vehicleGpsMessageDTO.size());

                   VehicleGpsMessageDTO vehicleGpsEventPublishing = vehicleGpsMessageDTO.getFirst();

                   String expectedRoutingKey = "v1.gps." + vehicleGpsEventPublishing.city() + "." + vehicleGpsEventPublishing.travelId();

                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(VehicleGpsMessageDTO.class));

                               verify(rabbitTemplate, times(1)).convertAndSend(
                                       eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                                       eq(expectedRoutingKey),
                                       any(GpsPayload.class),
                                       any(MessagePostProcessor.class));
                           });
               }

               @Test
               @DisplayName("Garante que os dados recebidos no checkpoint não sejam alterados durante o processamento (VehicleLocationRequestDTO -> VehicleGpsMessageDTO -> GpsPayload)")
               void shouldBuildGpsPayloadWithVehicleLocationData() throws Exception {
                   setupCommonMocksFromEventPublishing();

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<VehicleGpsMessageDTO> vehicleGpsMessageDTO = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();

                   assertEquals(1, vehicleGpsMessageDTO.size());

                   VehicleGpsMessageDTO vehicleGpsEventPublishing = vehicleGpsMessageDTO.getFirst();

                   ArgumentCaptor<GpsPayload> GpsPayloadArgCaptor = ArgumentCaptor.forClass(GpsPayload.class);

                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(VehicleGpsMessageDTO.class));

                               verify(rabbitTemplate, times(1))
                                       .convertAndSend(any(), any(), GpsPayloadArgCaptor.capture(), any(MessagePostProcessor.class));

                           });

                   GpsPayload capturedPayload = GpsPayloadArgCaptor.getValue();
                   assertNotNull(capturedPayload);

                   assertEquals(requestDTO.latitude(), capturedPayload.latitude());
                   assertEquals(requestDTO.longitude(), capturedPayload.longitude());
                   assertEquals(requestDTO.speed(), capturedPayload.speed());
                   assertEquals(requestDTO.heading(), capturedPayload.heading());
                   assertEquals(UUID.fromString(vehicleGpsEventPublishing.city()), capturedPayload.cityId());
                   assertEquals(vehicleGpsEventPublishing.travelId(), capturedPayload.travelId().toString());
                   assertNotNull(capturedPayload.timestamp());
               }

               @Test
               @DisplayName("Deve validar o envio da mensagem como NON_PERSISTENT (não persistente)")
               void shouldSendGpsMessageAsNonPersistent() throws Exception {
                   setupCommonMocksFromEventPublishing();

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<VehicleGpsMessageDTO> vehicleGpsMessageDTO = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();

                   assertEquals(1, vehicleGpsMessageDTO.size());

                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(VehicleGpsMessageDTO.class));

                               verify(rabbitTemplate, times(1))
                                       .convertAndSend(any(), any(), any(GpsPayload.class),
                                               argThat((MessagePostProcessor mpp)  -> {
                                                   // simula comportamento do MessageProperties
                                                   Message mockMessage = mock(Message.class);
                                                   MessageProperties mockMessageProperties =  mock(MessageProperties.class);
                                                   when(mockMessage.getMessageProperties()).thenReturn(mockMessageProperties);

                                                   // executa processador real
                                                   mpp.postProcessMessage(mockMessage);

                                                   verify(mockMessageProperties).setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);

                                                   return true;
                                               }));

                           });
               }

               @Test
               @DisplayName("Deve garantir que o evento publicado pelo markDriverCheckpoint seja efetivamente consumido pelo listener correto")
               void shouldProcessVehicleGpsEventAsynchronously() throws Exception {
                   setupCommonMocksFromEventPublishing();

                   mockMvc.perform(post(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(objectMapper.writeValueAsString(requestDTO)))
                           .andDo(print())
                           .andExpect(status().isOk());

                   List<VehicleGpsMessageDTO> vehicleGpsMessageDTO = applicationEvents.stream(VehicleGpsMessageDTO.class).toList();

                   assertEquals(1, vehicleGpsMessageDTO.size());

                   Awaitility.await().atMost(5, TimeUnit.SECONDS)
                           .untilAsserted(() -> {
                               verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(VehicleGpsMessageDTO.class));

                               verify(rabbitTemplate, times(1))
                                       .convertAndSend(any(), any(), any(GpsPayload.class),
                                               argThat((MessagePostProcessor mpp)  -> {
                                                   // simula comportamento do MessageProperties
                                                   Message mockMessage = mock(Message.class);
                                                   MessageProperties mockMessageProperties =  mock(MessageProperties.class);
                                                   when(mockMessage.getMessageProperties()).thenReturn(mockMessageProperties);

                                                   // executa processador real
                                                   mpp.postProcessMessage(mockMessage);

                                                   verify(mockMessageProperties).setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);

                                                   return true;
                                               }));

                           });
               }


           }

           @Nested
           class failureScenarios {

               @ParameterizedTest
               @DisplayName("Não deve enviar nada quando CityId ou TravelId provindos do VehicleGpsMessageDTO forem nulos ")
               @MethodSource("invalidVehicleGpsMsgProvider")
               void shouldNotSendGpsWhenCityIdOrTravelIdIsNull(VehicleGpsMessageDTO invalidVehicleGpsMsgDTO) {
                   setupCommonMocksFromEventPublishing();

                   gpsDataIngestorService.sendVehicleGps(invalidVehicleGpsMsgDTO);

                   verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(GpsPayload.class), any(MessagePostProcessor.class));
               }

               public static Stream<Arguments> invalidVehicleGpsMsgProvider() {
                   UUID validTravelId = UUID.randomUUID();
                   String validCityId = "valid-city-id";

                   VehicleLocationRequestDTO validLocation = new VehicleLocationRequestDTO(
                           validTravelId, -12.975, -38.502, 60.0, 180.0
                   );

                   return Stream.of(
                           Arguments.of(new VehicleGpsMessageDTO(null, validTravelId.toString(), validLocation)),
                           Arguments.of(new VehicleGpsMessageDTO(validCityId, null, validLocation))
                   );
               }

               @Test
               @DisplayName("Não deve enviar nada quando a conversão do CityId para String falhar")
               void shouldNotSendGpsWhenCityIdIsInvalid() {
                   setupCommonMocksFromEventPublishing();

                   String invalidCityId = "invalid-uuid-string";
                   UUID validTravelId = UUID.randomUUID();

                   VehicleGpsMessageDTO invalidMessage = new VehicleGpsMessageDTO(
                           invalidCityId,
                           validTravelId.toString(),
                           new VehicleLocationRequestDTO(validTravelId, -12.975, -38.502, 60.0, 180.0)
                   );

                   assertDoesNotThrow(() -> gpsDataIngestorService.sendVehicleGps(invalidMessage));

                   verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(GpsPayload.class), any(MessagePostProcessor.class));
               }
           }

           @Nested
           @DisplayName("Valida o funcionamento do circutBreaker em prol do processamento do método")
           class circuitBreakerScenarios {
               VehicleGpsMessageDTO vehicleGpsMessageDTO;

               @BeforeEach
               void setUp() {
                   circuitBreaker = circuitBreakerRegistry.circuitBreaker("gpsIngestor");
                   circuitBreaker.transitionToClosedState();
                   circuitBreaker.reset(); // zera os contadores

                   vehicleGpsMessageDTO = new VehicleGpsMessageDTO(
                           city.getId().toString(),
                           travel.getId().toString(),
                           new VehicleLocationRequestDTO(travel.getId(), -12.975, -38.502, 60.0, 180.0)
                   );
               }

               @Test
               @DisplayName("Deve validar o envio quando o CircuitBreaker estiver Closed")
               void shouldSendGpsWhenCircuitBreakerIsClosed() {
                   setupCommonMocksFromEventPublishing();

                   gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

                   verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), any(GpsPayload.class), any(MessagePostProcessor.class));
               }

               @Test
               @DisplayName("Deve descartar o ping de GPS quando o CircuitBreaker estiver Open")
               void shouldDiscardGpsWhenCircuitBreakerIsOpen() {
                   circuitBreaker = circuitBreakerRegistry.circuitBreaker("gpsIngestor");
                   circuitBreaker.transitionToOpenState();

                   setupCommonMocksFromEventPublishing();

                   assertDoesNotThrow(() -> gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO));

                   verify(rabbitTemplate, never()).convertAndSend(
                           any(String.class),
                           any(String.class),
                           any(GpsPayload.class),
                           any(MessagePostProcessor.class)
                   );
               }

               @Test
               @DisplayName("Não deve propagar exceptiion quando o RabbitMQ falhar")
               void shouldHandleRabbitMqFailureWithoutPropagatingException() {
                   setupCommonMocksFromEventPublishing();

                   doThrow(new AmqpException("")).when(rabbitTemplate).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));

                   assertDoesNotThrow(() -> gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO));

                   verify(rabbitTemplate, times(1)).convertAndSend(
                           any(String.class),
                           any(String.class),
                           any(GpsPayload.class),
                           any(MessagePostProcessor.class)
                   );
               }

               @Test
               @DisplayName("Garante que sucessivas falhas do RabbitMQ façam o Circuit Breaker mudar de CLOSED para OPEN")
               void shouldOpenCircuitBreakerAfterRepeatedRabbitMqFailures() {
                   // importante: o valor de numberOfFailsToOpenCircuit é provido do app.properties; sempre usar valor real

                   setupCommonMocksFromEventPublishing();

                   doThrow(new AmqpException("")).when(rabbitTemplate).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));

                   int numberOfFailsToOpenCircuit = 5; // app.properties
                   for (int i = 0; i <= numberOfFailsToOpenCircuit; i++) {
                       assertDoesNotThrow(() -> gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO));
                   }

                   assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

                   verify(rabbitTemplate, times(numberOfFailsToOpenCircuit)).convertAndSend(
                           any(String.class),
                           any(String.class),
                           any(GpsPayload.class),
                           any(MessagePostProcessor.class)
                   );

                   // nova chamada extra para garantir que está sendo bloqueada
                   assertDoesNotThrow(() -> gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO));

                   // rabbitmq não deve ter sido chamadao pela valor de numberOfFailsToOpenCircuit + 1 vez
                   verify(rabbitTemplate, times(numberOfFailsToOpenCircuit)).convertAndSend(
                           any(String.class),
                           any(String.class),
                           any(GpsPayload.class),
                           any(MessagePostProcessor.class)
                   );
               }

               @Test
               @DisplayName("Garante que o sistema volte a tentar se comunicar após o período de espera HALF-OPEN -> CLOSED")
               void shouldAllowGpsRetryWhenCircuitBreakerIsHalfOpen() {
                   setupCommonMocksFromEventPublishing();

                   // define o circuitbreaker em half para forçar de vez a transição
                   circuitBreaker.transitionToHalfOpenState();

                   gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

                   assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

                   // deve chamar o rabbitmq
                   verify(rabbitTemplate, times(1)).convertAndSend(
                           any(String.class),
                           any(String.class),
                           any(GpsPayload.class),
                           any(MessagePostProcessor.class));
               }
           }
        }

    }

    @Nested
    class processNewLocation {
        City city;
        Driver driver;
        Travel travel;
        Customer customer;

        VehicleLocationRequestDTO requestDTO;
        TravelCacheDTO travelCacheDTO;
        RouteDeviationDTO routeDeviationDTO;
        RouteDetailsDTO routeDetailsDTO;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            Permissions permission = new Permissions("ROLE_DRIVER");
            permissionsRepository.save(permission);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driver.setPermissions(List.of(permission));
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer);
            travel = travelRepository.saveAndFlush(travel);

            travelCacheDTO = new TravelCacheDTO(travel.getId(), TravelStatus.TRAVELLING, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());
            requestDTO = new VehicleLocationRequestDTO(travel.getId(), -12.9750, -38.5020, 60.0, 180.0);
            routeDeviationDTO = new RouteDeviationDTO(10.3, true, -32.232, -11.433);
            routeDetailsDTO = new RouteDetailsDTO(3100.0, 14500.0, "recalculated_polyline");

            ROUTE_KEY_PREFIX = "travel:route:" + travel.getId();
            TRACKING_KEY_PREFIX = "travel:tracking:" + travel.getId();
            STUDENT_TRAVEL_KEY_PREFIX = "travel:away_students:" + travel.getId();
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve garantir que uma nova localização seja processada normalmente quando o ônibus não percorreu a distância mínima para revalidação")
            void shouldProcessNewLocationWithoutRouteRecalculation() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");

                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "120.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_test");

                long previousTimestamp = clock.millis() - 5_000;
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaTimestamp", String.valueOf(previousTimestamp));

                TravelCacheDTO localCacheDTO = new TravelCacheDTO(travel.getId(), TravelStatus.TRAVELLING, -38.502, -12.975, "encoded_geometry_test", 8005.0, 120.0);

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(localCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(30.0);

                travelTrackingService.processNewLocation(requestDTO);

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(travel.getId());
                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                verify(routeCalculationService, never()).isRouteDeviation(any());
                verifyNoInteractions(mapboxAPIService);

                assertEquals("encoded_geometry_test", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                assertEquals("-11.323", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                assertEquals("-38.993", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

                assertEquals("8005.0", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));

                Double newEta = Double.valueOf(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining").toString());
                assertTrue(newEta < 120.0, "O ETA deve ter sido recalculado internamente e ser menor que o anterior");
            }

            @Test
            @DisplayName("Deve garantir que, após atingir a distância mínima, o sistema valide o desvio, conclua que o ônibus permanece na rota e recalcule apenas o ETA interno")
            void shouldRecalculateEtaInternallyWhenVehicleRemainsOnRoute() {
                // getRouteCalculateReference
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");

                // getPreviousEta
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "120.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_test");

                long previousTimestamp = clock.millis() - 5_000;
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaTimestamp", String.valueOf(previousTimestamp));

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(55.0);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(new RouteDeviationDTO(10.3, false, -32.232, -11.433));

                travelTrackingService.processNewLocation(requestDTO);

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(any());
                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(routeCalculationService, times(1)).isRouteDeviation(any(RouteDeviationRequestDTO.class));

                Double newEta = Double.valueOf(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining").toString());
                assertTrue(newEta < 120.0, "O ETA deve ter sido recalculado internamente e ser menor que o anterior");

                assertEquals(travel.getDistance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                assertEquals("encoded_geometry_test", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                assertNotNull(redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "etaTimestamp"));

                verifyNoInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve garantir que o sistema use o mapbox quando detectar desvio de rota")
            void shouldRecalculateRouteUsingMapboxWhenVehicleLeavesRoute() {
                // getRouteCalculateReference
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");

                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_test");

                // getPreviousEta
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "120.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(55.0);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(routeDeviationDTO);
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.processNewLocation(requestDTO);

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(any());
                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(routeCalculationService, times(1)).isRouteDeviation(any(RouteDeviationRequestDTO.class));
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                assertEquals(routeDetailsDTO.geometry(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
                assertEquals(routeDetailsDTO.distance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                assertEquals(requestDTO.latitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
                assertEquals(requestDTO.longitude().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));
                assertEquals(routeDetailsDTO.duration().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining"));
                assertEquals(TravelStatus.TRAVELLING.name(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "status"));

            }

            @Test
            @DisplayName("Deve atualizar metadata da viagem após qualquer tipo de processamento")
            void shouldAlwaysUpdateTravelMetadata() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "old_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "120.0");
                long previousTimestamp = clock.millis() - 5_000;
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaTimestamp", String.valueOf(previousTimestamp));

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(55.0);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenReturn(new RouteDeviationDTO(55.0, true, requestDTO.latitude(), requestDTO.longitude()));
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.processNewLocation(requestDTO);

                String updatedGeometry = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry");
                String updatedDistance = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining");
                String updatedDuration = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining");
                String updatedStatus = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "status");

                assertNotNull(updatedGeometry);
                assertNotNull(updatedDistance);
                assertNotNull(updatedDuration);
                assertEquals(TravelStatus.TRAVELLING.name(), updatedStatus);

                assertEquals(routeDetailsDTO.geometry(), updatedGeometry);
                assertEquals(routeDetailsDTO.distance().toString(), updatedDistance);
                assertEquals(routeDetailsDTO.duration().toString(), updatedDuration);
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve rejeitar parâmetros do VehicleLocationRequestDTO null")
            @MethodSource("invalidVehicleLocationProvider")
            void shouldThrowExceptionWhenRequestIsNull(VehicleLocationRequestDTO vehicleLocationRequestDTO) {
                assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoInteractions(travelCacheService, mapboxAPIService, routeCalculationService);

                assertTrue(redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).isEmpty());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            public static Stream<Arguments> invalidVehicleLocationProvider() {
                return Stream.of(
                        Arguments.of(new VehicleLocationRequestDTO(null, -12.9750, -38.5020, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.5020, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.9750, null, 60.0, 180.0)),
                        Arguments.of( (VehicleLocationRequestDTO) null)
                );
            }

            @Test
            @DisplayName("Deve lançar EntityNotFoundException quando a viagem não existir")
            void shouldThrowExceptionWhenTravelDoesNotExist() {
                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenThrow(new EntityNotFoundException());

                assertThrows(EntityNotFoundException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                verifyNoInteractions(mapboxAPIService, routeCalculationService);

                assertTrue(redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).isEmpty());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar TravelException quando a viagem não estiver em andamento")
            @MethodSource("invalidTravelStatusProvider")
            void shouldRejectWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) {
                TravelCacheDTO cacheWithInvalidStatus = new TravelCacheDTO(travel.getId(), invalidTravelStatus, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(cacheWithInvalidStatus);

                assertThrows(TravelException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                verifyNoInteractions(mapboxAPIService, routeCalculationService);

                assertTrue(redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).isEmpty());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            public static Stream<Arguments> invalidTravelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.PENDING),
                        Arguments.of(TravelStatus.CANCELED),
                        Arguments.of(TravelStatus.FINISH)
                );
            }

            @Test
            @DisplayName("Deve lançar LiveLocationDataNotFoundException quando os dados de cálculo da rota estiverem null no redis")
            void shouldThrowExceptionWhenRouteReferenceIsMissing() {
                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                verifyNoInteractions(mapboxAPIService, routeCalculationService);

                assertTrue(redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).isEmpty());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            @Test
            @DisplayName("Deve lançar LiveLocationDataNotFoundException quando a geometry do redis for null")
            void shouldThrowExceptionWhenGeometryIsMissing() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.003");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                verifyNoInteractions(mapboxAPIService, routeCalculationService);

                assertEquals(2, redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).size());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            @Test
            @DisplayName("Deve lançar LiveLocationDataNotFoundException quando a distanceRemaining (distância restante) do redis for null")
            void shouldThrowExceptionWhenDistanceRemainingIsMissing() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.003");

                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                verifyNoInteractions(mapboxAPIService, routeCalculationService);

                assertEquals(3, redisTemplate.opsForHash().entries(ROUTE_KEY_PREFIX).size());
                assertTrue(redisTemplate.opsForHash().entries(TRACKING_KEY_PREFIX).isEmpty());
            }

            @ParameterizedTest
            @DisplayName("Deve lançar EtaDataStatesInvalidException quando o PreviousState, durationRemaining ou timestamp forem null")
            @MethodSource("invalidPreviousEtaProvider")
            void shouldThrowExceptionWhenPreviousEtaIsMissing(PreviousStateDTO invalidPreviousStateDTO) {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.003");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "5000.0");

                if (invalidPreviousStateDTO != null) {
                    if (invalidPreviousStateDTO.durationRemaining() != null) {
                        redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", invalidPreviousStateDTO.durationRemaining().toString());
                    }
                    if (invalidPreviousStateDTO.timeStamp() != null) {
                        redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaTimestamp", String.valueOf(invalidPreviousStateDTO.timeStamp()));
                    }
                }

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.3);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(60.3, false, -32.232, -11.433));

                EtaDataStatesInvalidException exception = assertThrows(EtaDataStatesInvalidException.class, () -> {
                    travelTrackingService.processNewLocation(requestDTO);
                });

                assertTrue(exception.getMessage().contains("dados do previousEta inválidos ou null"));

                verifyNoInteractions(mapboxAPIService);
            }

            public static Stream<Arguments> invalidPreviousEtaProvider() {
                return Stream.of(
                        Arguments.of(new PreviousStateDTO(null, null, 872382L)),
                        Arguments.of(new PreviousStateDTO(38.2, null, null)),
                        Arguments.of( (PreviousStateDTO) null)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar RecalculateEtaException quando o mapbox retornar null")
            @MethodSource("invalidRouteDetailsDTOProvider")
            void shouldThrowExceptionWhenMapboxReturnsNull(RouteDetailsDTO invalidRouteDetailsDTO) {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.003");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "5000.0");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.3);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(60.3, true, -32.232, -11.433));
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(invalidRouteDetailsDTO);

                RecalculateEtaException exception = assertThrows(RecalculateEtaException.class, () -> travelTrackingService.processNewLocation(requestDTO));

                assertEquals("[processNewLocation] resposta inválida da API de rotas", exception.getMessage());
            }

            public static Stream<Arguments> invalidRouteDetailsDTOProvider() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(null, 4000.0, null)),
                        Arguments.of(new RouteDetailsDTO(300.2, null, null)),
                        Arguments.of((RouteDetailsDTO) null )
                );
            }

        }

        @Nested
        @DisplayName("Responsável por validar os cálculos internos de ETA")
        class calculateEtaInternallyScenarios {
            @BeforeEach
            void setUpClock() {
                Instant fixedInstant = Instant.parse("2024-01-01T12:00:00Z");
                when(clock.millis()).thenReturn(fixedInstant.toEpochMilli());
            }

            @Test
            @DisplayName("Deve reduzir corretamente o ETA de acordo com o tempo decorrido")
            void shouldDecreaseEtaAccordingToElapsedTime() {
                long previousTimestamp = clock.millis() - 10_000L;

                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "5000.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "100.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaLastUpdatedAt", String.valueOf(previousTimestamp));

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(10.0);

                travelTrackingService.processNewLocation(requestDTO);

                String updatedDurationStr = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining");
                assertNotNull(updatedDurationStr, "O durationRemaining deve ter sido atualizado no Redis");

                double updatedDuration = Double.parseDouble(updatedDurationStr);
                // cálculo esperado 100.0 (anterior) - 10.0 (decorrido) = 90.0
                assertEquals(90.0, updatedDuration, 0.1, "O ETA deve ser reduzido exatamente pelo tempo decorrido");

                assertEquals(travel.getDistance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                assertEquals("encoded_geometry", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));
            }

            @Test
            @DisplayName("Deve nunca retornar ETA negativo (deve ser 0.0 quando tempo decorrido > ETA)")
            void shouldNeverReturnNegativeEta() {
                long previousTimestamp = clock.millis() - 20_000L;

                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "5000.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "5.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "etaLastUpdatedAt", String.valueOf(previousTimestamp));

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(10.0);

                travelTrackingService.processNewLocation(requestDTO);

                String updatedDurationStr = (String) redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining");
                assertNotNull(updatedDurationStr, "O durationRemaining deve ter sido atualizado no Redis");

                double updatedDuration = Double.parseDouble(updatedDurationStr);
                // cálculo esperado: Math.max(0.0, 5.0 - 20.0) = 0.0
                assertEquals(0.0, updatedDuration, 0.1, "O ETA nunca pode ser negativo, deve ser truncado em 0.0");

                assertEquals(travel.getDistance().toString(), redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
            }
        }

        @Nested
        @DisplayName("Responsável por validar os cenários de resposta do mapbox")
        class calculateEtaFromMapbox {
            // setup para jogar os testes direto ao cenário do mapbox
            private void setupCommonMocksForMapboxPath() {
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "5000.0");
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "durationRemaining", "100.0");

                when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(100.0);

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(100.0, true, requestDTO.latitude(), requestDTO.longitude()));
            }

            @Test
            @DisplayName("Deve utilizar e persistir a resposta completa retornada pelo Mapbox")
            void shouldReturnRouteDetailsReturnedByMapbox() {
                setupCommonMocksForMapboxPath();

                RouteDetailsDTO mapboxResponse = new RouteDetailsDTO(150.5, 6500.0, "new_mapbox_geometry");
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(mapboxResponse);

                travelTrackingService.processNewLocation(requestDTO);

                assertEquals("150.5", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "durationRemaining"));
                assertEquals("6500.0", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
                assertEquals("new_mapbox_geometry", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));

                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            @Test
            @DisplayName("Deve rejeitar resposta do Mapbox sem duration")
            void shouldRejectMapboxResponseWithoutDuration() {
                setupCommonMocksForMapboxPath();

                RouteDetailsDTO invalidResponse = new RouteDetailsDTO(null, 6500.0, "new_mapbox_geometry");
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(invalidResponse);

                RecalculateEtaException exception = assertThrows(
                        RecalculateEtaException.class,
                        () -> travelTrackingService.processNewLocation(requestDTO)
                );

                assertTrue(exception.getMessage().contains("resposta inválida da API de rotas"));
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            @Test
            @DisplayName("Deve rejeitar resposta do Mapbox sem distance")
            void shouldRejectMapboxResponseWithoutDistance() {
                setupCommonMocksForMapboxPath();

                RouteDetailsDTO invalidResponse = new RouteDetailsDTO(150.5, null, "new_mapbox_geometry");
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(invalidResponse);

                RecalculateEtaException exception = assertThrows(
                        RecalculateEtaException.class,
                        () -> travelTrackingService.processNewLocation(requestDTO)
                );

                assertTrue(exception.getMessage().contains("resposta inválida da API de rotas"));
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }

            @Test
            @DisplayName("Deve rejeitar resposta nula do Mapbox")
            void shouldRejectNullMapboxResponse() {
                setupCommonMocksForMapboxPath();

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(null);

                RecalculateEtaException exception = assertThrows(
                        RecalculateEtaException.class,
                        () -> travelTrackingService.processNewLocation(requestDTO)
                );

                assertTrue(exception.getMessage().contains("resposta inválida da API de rotas"));
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }
        }
    }

    @Nested
    class getDriverPosition {
        City city;
        Customer customer;
        Travel travel;
        Driver driver;

        TravelCacheDTO travelCacheDTO;

        String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer);
            travel = travelRepository.saveAndFlush(travel);

            travelCacheDTO = new TravelCacheDTO(travel.getId(), TravelStatus.TRAVELLING, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

            ROUTE_KEY_PREFIX = "travel:route:" + travel.getId();
            TRACKING_KEY_PREFIX = "travel:tracking:" + travel.getId();

            completePathController = DEFAULT_CONTROLLER_PATH + "/travels/" + travel.getId() + "/location";
        }

        @Test
        @DisplayName("Deve retornar a localização atual do motorista")
        void shouldReturnDriverCurrentPositionSuccessfully() throws Exception {
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");

            redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-11.563");
            redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-39.003");

            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_test");

            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

            MvcResult result = mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            assertNotNull(result.getResponse());

            assertEquals("-11.323", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
            assertEquals("-38.993", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lng"));

            assertEquals("-11.563", redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
            assertEquals("-39.003", redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lng"));

            assertEquals("8005.0", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "distanceRemaining"));
            assertEquals("encoded_geometry_test", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "geometry"));


        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando a viagem não estiver em andamento (travelling)")
        @MethodSource("invalidTravelStatusProvider")
        void shouldRejectDriverPositionWhenTravelIsPending(TravelStatus invalidTravelStatus) throws Exception {
            TravelCacheDTO invalidCache = new TravelCacheDTO(travel.getId(), invalidTravelStatus, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(invalidCache);

            mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        public static Stream<Arguments> invalidTravelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.CANCELED),
                    Arguments.of(TravelStatus.FINISH),
                    Arguments.of(TravelStatus.PENDING)
            );
        }

        @Test
        @DisplayName("Deve lançar exception quando a viagem não existir no banco")
        void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenThrow(new EntityNotFoundException());

            mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Deve lançar exception quando não existr localização armazenada no Reds")
        void shouldRejectDriverPositionWhenLiveLocationDoesNotExist() throws Exception {
            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

            mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Deve garantir que o endpoint devolva a última localização estável registrada")
        void shouldReturnLastStoredDriverPositionSuccessfully() throws Exception {
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lat", "-11.323");
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "last_calc_lng", "-38.993");

            redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lat", "-11.563");
            redisTemplate.opsForHash().put(TRACKING_KEY_PREFIX, "current_lng", "-39.003");

            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "distanceRemaining", "8005.0");
            redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "geometry", "encoded_geometry_test");

            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);

            MvcResult result = mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            String jsonResponse = result.getResponse().getContentAsString();
            LiveLocationDTO responseLiveLocationDTO = objectMapper.readValue(jsonResponse, LiveLocationDTO.class);

            assertEquals(-11.323, responseLiveLocationDTO.lastCalcLat());
            assertEquals(-38.993, responseLiveLocationDTO.lastCalcLng());
            assertEquals(-11.563, responseLiveLocationDTO.latitude());
            assertEquals(-39.003, responseLiveLocationDTO.longitude());
            assertEquals(8005.0, responseLiveLocationDTO.distance());
            assertEquals("encoded_geometry_test", responseLiveLocationDTO.geometry());

            // get não deve ter alterado os valores do redis
            assertEquals("-11.323", redisTemplate.opsForHash().get(ROUTE_KEY_PREFIX, "last_calc_lat"));
            assertEquals("-11.563", redisTemplate.opsForHash().get(TRACKING_KEY_PREFIX, "current_lat"));
        }
    }

    @Nested
    class getTravelHistory {
        City city;
        Customer customer;
        Travel travel;
        Driver driver;

        TravelCacheDTO travelCacheDTO;

        String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer);
            travel = travelRepository.saveAndFlush(travel);

            travelCacheDTO = new TravelCacheDTO(travel.getId(), TravelStatus.TRAVELLING, -38.5020, -12.9750, travel.getPolylineRoute(), travel.getDistance(), travel.getDuration());

            ROUTE_KEY_PREFIX = "travel:route:" + travel.getId();
            TRACKING_KEY_PREFIX = "travel:tracking:" + travel.getId();

            completePathController = DEFAULT_CONTROLLER_PATH + "/travels/" + travel.getId() + "/history";
        }

        @Test
        @DisplayName("Deve retornar histórico de localização com sucesso, ordenado e isolado por viagem")
        void shouldReturnTravelLocationHistorySuccessfully() throws Exception {
            TravelLocationHistory record1 = new TravelLocationHistory();
            record1.setTravelId(travel.getId());
            record1.setCityId(city.getId());
            record1.setLatitude(-11.500);
            record1.setLongitude(-38.900);
            record1.setTimestamp(Instant.now().minusSeconds(30));

            TravelLocationHistory record2 = new TravelLocationHistory();
            record2.setTravelId(travel.getId());
            record2.setCityId(city.getId());
            record2.setLatitude(-11.400);
            record2.setLongitude(-38.800);
            record2.setTimestamp(Instant.now().minusSeconds(20));

            TravelLocationHistory record3 = new TravelLocationHistory();
            record3.setTravelId(travel.getId());
            record3.setCityId(city.getId());
            record3.setLatitude(-11.600);
            record3.setLongitude(-39.000);
            record3.setTimestamp(Instant.now().minusSeconds(10));

            Travel otherTravel = new Travel(/* parâmetros necessários */);
            otherTravel.setCustomer(customer);
            otherTravel = travelRepository.saveAndFlush(otherTravel);

            TravelLocationHistory otherTravelRecord = new TravelLocationHistory();
            otherTravelRecord.setTravelId(otherTravel.getId());
            otherTravelRecord.setCityId(city.getId());
            otherTravelRecord.setLatitude(-22.000);
            otherTravelRecord.setLongitude(-43.000);
            otherTravelRecord.setTimestamp(Instant.now());

            travelLocationHistoryRepository.saveAllAndFlush(Arrays.asList(
                    record1, record2, record3, otherTravelRecord
            ));

            MvcResult result = mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            String responseJson = result.getResponse().getContentAsString();
            assertNotNull(responseJson, "O body da resposta não deve ser nulo");
            assertFalse(responseJson.isBlank(), "O body da resposta não deve estar vazio");

            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode contentNode = rootNode.get("content");
            assertNotNull(contentNode, "O campo 'content' deve existir na resposta");

            List<LocationPointDTO> content = objectMapper.convertValue(
                    contentNode,
                    new TypeReference<List<LocationPointDTO>>() {}
            );

            long totalElements = rootNode.get("page").get("totalElements").asLong();
            assertEquals(3, totalElements, "Deve retornar apenas os 3 registros da viagem principal");

            assertEquals(3, content.size(), "O conteúdo deve ter exatamente 3 registros");

            // ordem por timestamp ASC (mais antigo primeiro)
            assertEquals(-11.500, content.get(0).latitude(), 0.001, "Primeiro ponto (30s atrás)");
            assertEquals(-38.900, content.get(0).longitude(), 0.001, "Longitude do primeiro ponto incorreta");

            assertEquals(-11.400, content.get(1).latitude(), 0.001, "Segundo ponto (20s atrás)");
            assertEquals(-38.800, content.get(1).longitude(), 0.001, "Longitude do segundo ponto incorreta");

            assertEquals(-11.600, content.get(2).latitude(), 0.001, "Terceiro ponto (10s atrás)");
            assertEquals(-39.000, content.get(2).longitude(), 0.001, "Longitude do terceiro ponto incorreta");

            // timestamp deve estar presente
            content.forEach(point -> assertNotNull(point.timestamp()));

            boolean containsOtherTravelData = content.stream()
                    .anyMatch(dto -> dto.latitude().equals(-22.000) || dto.longitude().equals(-43.000));
            assertFalse(containsOtherTravelData);
        }

        @Test
        @DisplayName("Deve retornar página vazia quando a viagem não possuir histórico de localização")
        void shouldReturnEmptyPageWhenTravelHasNoLocationHistory() throws Exception {
            MvcResult result = mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            String responseJson = result.getResponse().getContentAsString();
            assertNotNull(responseJson, "O body da resposta não deve ser nulo");
            assertFalse(responseJson.isBlank(), "O body da resposta não deve estar vazio");

            JsonNode rootNode = objectMapper.readTree(responseJson);

            JsonNode contentNode = rootNode.get("content");
            assertNotNull(contentNode, "O campo 'content' deve existir na resposta");
            assertTrue(contentNode.isArray(), "O campo 'content' deve ser um array");
            assertEquals(0, contentNode.size(), "O conteúdo deve estar vazio (nenhum registro histórico)");

            long totalElements = rootNode.get("page").get("totalElements").asLong();
            assertEquals(0, totalElements, "totalElements deve ser 0 quando não há histórico");

            long totalPages = rootNode.get("page").get("totalPages").asLong();
            assertEquals(0, totalPages, "totalPages deve ser 0 quando não há registros");

            List<LocationPointDTO> content = objectMapper.convertValue(
                    contentNode,
                    new TypeReference<List<LocationPointDTO>>() {}
            );
            assertTrue(content.isEmpty(), "A lista desserializada deve estar vazia");
        }

        @Test
        @DisplayName("Garantir que o método não execute consulta ao banco quando o identificador obrigatório não estiver presente")
        void shouldRejectTravelHistoryRequestWhenTravelIdIsNull() throws Exception {
            completePathController = DEFAULT_CONTROLLER_PATH + "/travels/" + null + "/history";

            MvcResult result = mockMvc.perform(get(completePathController).with(user(authUser).authorities(new SimpleGrantedAuthority(driverRole)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest()).andReturn();

            Exception resolvedException = result.getResolvedException();
            assertNotNull(resolvedException);
        }
    }
}