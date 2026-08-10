package com.travel_system.backend_app.integration.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelCacheDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NamedStoredProcedureQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.org.checkerframework.framework.qual.DefaultQualifierForUse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.hamcrest.Matchers.nullValue;

public class TravelControllerIT extends IntegrationTestBase {

    @Autowired
    private TravelRepository travelRepository;
    @Autowired
    private StudentTravelRepository studentTravelRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private TravelReportsRepository travelReportsRepository;
    @Autowired
    private TravelLocationHistoryRepository travelLocationHistoryRepository;
    @Autowired
    private GeoPositionRepository geoPositionRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private TravelNotificationService travelNotificationService;
    @MockitoBean
    private TravelCacheService travelCacheService;
    @MockitoBean
    private TravelStudentStateCacheService travelStudentStateCacheService;

    private final String PATH_CONTROLLER = "/v1/travel";
    private final String AUTH_USER = "authenticated_user";

    @BeforeEach
    void setUp() {
        // limpa as chaves de notificação do redis a cada teste
        Set<String> notificationKeys = redisTemplate.keys("notification:*");
        if (notificationKeys != null && !notificationKeys.isEmpty()) {
            redisTemplate.delete(notificationKeys);
        }

        Set<String> travelKeys = redisTemplate.keys("travelId:*");
        if (travelKeys != null && !travelKeys.isEmpty()) {
            redisTemplate.delete(travelKeys);
        }

        // limpa a cada teste (obs: a ordem É IMPORTANTE)
        studentTravelRepository.deleteAll();
        travelReportsRepository.deleteAll();
        travelRepository.deleteAll();
        studentRepository.deleteAll();
        driverRepository.deleteAll();
        customerRepository.deleteAll();
        cityRepository.deleteAll();
        travelLocationHistoryRepository.deleteAll();
    }

    @Nested
    class createTravel {
        Customer customer;
        Driver driver;
//        Travel travel;
        City city;

        TravelPreviewDTO travelPreviewDTO;
        TravelRequestDTO travelRequestDTO;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

//            travel = new Travel(null, null, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:10:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer);
//            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING, -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

            travelPreviewDTO = new TravelPreviewDTO(123.45, 2.5, "Feira de Santana", "14:30");
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve validar o fluxo completo da criação de uma nova viagem com todos os dados válidos")
            void shouldCreateTravelSuccessfully() throws Exception {
                when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(travelPreviewDTO);
                doNothing().when(travelNotificationService).sendTravelCreatedNotification(any(Travel.class));

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isCreated());

                Travel persistedTrip = travelRepository.findAll().getFirst();

                assertEquals(1, travelRepository.count()); // deve existir apenas um registro

                assertNotNull(persistedTrip);
                assertNotNull(persistedTrip.getCreatedAt());
                assertNotNull(persistedTrip.getDestinationCity());

                assertEquals(persistedTrip.getDriver().getId(), driver.getId());
                assertEquals(TravelStatus.PENDING, persistedTrip.getTravelStatus());
                assertEquals(persistedTrip.getDistance(), travelPreviewDTO.distance());
                assertEquals(persistedTrip.getDuration(), travelPreviewDTO.duration());

                verify(travelNotificationService, times(1)).sendTravelCreatedNotification(any(Travel.class));
            }

            @Test
            @DisplayName("Deve garantir que as informações retornadas pela API do MAPBOX sejam persistidas corretamente")
            void shouldPersistPreviewInformationReturnedByMapbox() throws Exception {
                when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(travelPreviewDTO);

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isCreated());

                Travel persistedTrip = travelRepository.findAll().getFirst();

                assertNotNull(persistedTrip.getDestinationCity());

                assertEquals(persistedTrip.getDistance(), travelPreviewDTO.distance());
                assertEquals(persistedTrip.getDuration(), travelPreviewDTO.duration());
            }

            @Test
            @DisplayName("Deve realizar a validação da resposta do DTO")
            void shouldReturnTravelPreviewInResponse() throws Exception {
                when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(travelPreviewDTO);

                MvcResult result = mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isCreated()).andReturn();

                String jsonResponse = result.getResponse().getContentAsString();
                TravelResponseDTO travelResponseDTO = objectMapper.readValue(jsonResponse, TravelResponseDTO.class);

                Travel persistedTrip = travelRepository.findAll().getFirst();

                assertNotNull(persistedTrip);

                assertEquals(persistedTrip.getId(), travelResponseDTO.id());
                assertEquals(persistedTrip.getTravelStatus(), travelResponseDTO.status());
                assertEquals(persistedTrip.getDriver().getId(), travelResponseDTO.driverResponseDTO().id());
                assertEquals(travelPreviewDTO.distance(), travelResponseDTO.travelPreviewDTO().distance());
                assertEquals(travelPreviewDTO.duration(), travelResponseDTO.travelPreviewDTO().duration());

                assertNotNull(travelResponseDTO.travelPreviewDTO().arrivalTime());
            }
        }
        
        @Nested 
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o motorista não existir")
            void shouldReturnNotFoundWhenDriverDoesNotExist() throws Exception {
                TravelRequestDTO withoutDriver = new TravelRequestDTO(UUID.randomUUID(), TravelPeriod.MORNING, -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(withoutDriver)))
                        .andDo(print())
                        .andExpect(status().isNotFound());

                assertEquals(0, travelRepository.count());
            }

            @Test
            @DisplayName("Deve lançar exception quando o motorista estiver inativo")
            void shouldRejectTravelCreationWhenDriverIsInactive() throws Exception {
                driver.setStatus(GeneralStatus.INACTIVE);
                driverRepository.save(driver);

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isBadRequest());

                assertEquals(0, travelRepository.count());
            }

            @Test
            @DisplayName("Deve lançar exception quando o motorista já estiver com alguma viagem ativa no momento da criação da viagem")
            void shouldRejectTravelCreationWhenDriverAlreadyHasActiveTravel() throws Exception {
                Travel travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:10:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
                travelRepository.save(travel);

                driver.setTravels(List.of(new Travel()));
                driverRepository.save(driver);

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isConflict());

                assertEquals(1, travelRepository.count()); // já possui uma viagem persistida para o driver, mas não deve criar uma nova
            }

            @Test
            @DisplayName("Deve lançar exception quando o período da viagem não for informado")
            void shouldRejectTravelCreationWhenTravelPeriodIsNull() throws Exception {
                TravelRequestDTO withoutPeriod = new TravelRequestDTO(driver.getId(), null, -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(withoutPeriod)))
                        .andDo(print())
                        .andExpect(status().isConflict());

                assertEquals(0, travelRepository.count());
            }

            @Test
            @DisplayName("Deve lançar exception caso o mapbox não retorne os dados de preview da viagem corretamente")
            void shouldFailWhenMapboxReturnsNullPreview() throws Exception {
                when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(null);

                mockMvc.perform(post(PATH_CONTROLLER + "/" + "create").with(user(AUTH_USER)
                                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(travelRequestDTO)))
                        .andDo(print())
                        .andExpect(status().isBadGateway());

                assertEquals(0, travelRepository.count());
            }

        }
    }

   @Nested
    class startTravel {
        Customer customer;
        City city;
        Driver driver;
        Travel travel;

        TravelRequestDTO travelRequestDTO;
        RouteDetailsDTO routeDetailsDTO;

       private String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), null, TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING, -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

            routeDetailsDTO = new RouteDetailsDTO(1000.0, 36.3, "encoded_polyline_route");

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/start";
        }

       @Test
       @DisplayName("Deve validar todo o fluxo de início de uma viagem já criada (status = pending)")
       void shouldStartTravelSuccessfully() throws Exception {
            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(routeDetailsDTO);

            mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertEquals(1, travelRepository.count()); // deve manter a viagem persistida

           assertEquals(TravelStatus.TRAVELLING, persistedTrip.getTravelStatus());
           assertEquals(persistedTrip.getDistance(), routeDetailsDTO.distance());
           assertEquals(persistedTrip.getDuration(), routeDetailsDTO.duration());
           assertEquals(persistedTrip.getPolylineRoute(), routeDetailsDTO.geometry());

           assertNotNull(persistedTrip.getStartHourTravel());

           verify(travelCacheService, times(1)).invalidateTravelStaticCache(travel.getId());
           verify(travelNotificationService, times(1)).sendTravelStartedNotification(any(Travel.class));

       }

       @Test
       @DisplayName("Deve persistir corretamente os dados da rota")
       void shouldPersistCalculatedRouteInformationWhenTravelStarts() throws Exception {
            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(routeDetailsDTO);

           mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                           .contentType(MediaType.APPLICATION_JSON))
                   .andDo(print())
                   .andExpect(status().isNoContent());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertEquals(persistedTrip.getDistance(), routeDetailsDTO.distance());
           assertEquals(persistedTrip.getDuration(), routeDetailsDTO.duration());
           assertEquals(persistedTrip.getPolylineRoute(), routeDetailsDTO.geometry());
       }

       @Test
       @DisplayName("Deve garantir que o horário de início da viagem seja registrado")
       void shouldSetTravelStartTimeWhenTravelStarts() throws Exception {
           when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(routeDetailsDTO);

           mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                           .contentType(MediaType.APPLICATION_JSON))
                   .andDo(print())
                   .andExpect(status().isNoContent());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertNotNull(persistedTrip.getStartHourTravel());
        }

       @Test
       void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
           mockMvc.perform(post(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/start").with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                           .contentType(MediaType.APPLICATION_JSON))
                   .andDo(print())
                   .andExpect(status().isNotFound());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertEquals(1, travelRepository.count());

           assertEquals(TravelStatus.PENDING, persistedTrip.getTravelStatus());

           assertNull(persistedTrip.getStartHourTravel());

           verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
       }

       @ParameterizedTest
       @DisplayName("Não deve continuar o ínicio da viagem caso a viagem já esteja finalizada, em andamento ou cancelada")
       @MethodSource("travelStatusProvider")
       void shouldRejectStartWhenTravelStatusIsInvalid(TravelStatus invalidTravelStatus) throws Exception {
            travel.setTravelStatus(invalidTravelStatus);
            travelRepository.save(travel);

           mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                           .contentType(MediaType.APPLICATION_JSON))
                   .andDo(print())
                   .andExpect(status().isConflict());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertEquals(1, travelRepository.count());
           assertEquals(invalidTravelStatus, persistedTrip.getTravelStatus());
           assertNull(persistedTrip.getStartHourTravel());

           verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());

       }

       public static Stream<Arguments> travelStatusProvider() {
           return Stream.of(
                   Arguments.of(TravelStatus.FINISH),
                   Arguments.of(TravelStatus.TRAVELLING),
                   Arguments.of(TravelStatus.CANCELED)
           );
       }

       @ParameterizedTest
       @DisplayName("Deve lançãr exception quando o mapbox retornar null ou dados incompletos")
       @MethodSource("invalidRouteDetailsProvider")
       void shouldFailWhenMapboxReturnsNullRouteDetailsOrInvalidData(RouteDetailsDTO invalidRouteDetailsDTO) throws Exception {
           when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList())).thenReturn(invalidRouteDetailsDTO);

           mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                           .contentType(MediaType.APPLICATION_JSON))
                   .andDo(print())
                   .andExpect(status().isBadGateway());

           Travel persistedTrip = travelRepository.findAll().getFirst();

           assertEquals(TravelStatus.PENDING, persistedTrip.getTravelStatus());
           assertNull(persistedTrip.getStartHourTravel());

           verifyNoInteractions(travelCacheService, travelNotificationService);
       }

       public static Stream<Arguments> invalidRouteDetailsProvider() {
           return Stream.of(
                   Arguments.of(new RouteDetailsDTO(null, 3000.0, "encoded_geometry")),
                   Arguments.of(new RouteDetailsDTO(120.0, null, "encoded_geometry")),
                   Arguments.of(new RouteDetailsDTO(120.0, 3000.0, null)),
                   Arguments.of((RouteDetailsDTO) null)
           );
       }
   }

     @Nested
    class endTravel {
        City city;
        Customer customer;
        Driver driver;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        TravelRequestDTO travelRequestDTO;

        private String completePathController;

        private String TRACKING_KEY_PREFIX;
        private String ROUTE_KEY_PREFIX;
        private String STUDENT_TRAVEL_KEY_PREFIX;
        private String STUDENT_AWAY_STATE_LOCK;
        private String SET_KEY;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING, -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/end";

            TRACKING_KEY_PREFIX = "travel:tracking:" + travel.getId();
            ROUTE_KEY_PREFIX = "travel:route:" + travel.getId();
            STUDENT_TRAVEL_KEY_PREFIX = "travel:away_students:";
            STUDENT_AWAY_STATE_LOCK = "travel:student-away-lock:";
            SET_KEY = "ACTIVE_TRAVELS_KEY";
        }

        @Nested
        class successScenarios {
            
            @Test
            @DisplayName("Deve encerrar a viagem, gerar relatórios e limpar cache com sucesso")
            void shouldEndTravelWithSuccess() throws Exception {
                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel result = travelRepository.findById(travel.getId()).orElseThrow();
                List<TravelReports> travelReports = travelReportsRepository.findAll();

                assertEquals(TravelStatus.FINISH, result.getTravelStatus());
                assertNotNull(result.getEndHourTravel());

                assertEquals(1, travelReports.size());
                TravelReports report = travelReports.get(0);
                assertEquals(travel.getId(), report.getTravel().getId());

                assertTrue(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()).isEmpty());
                assertFalse(redisTemplate.hasKey(ROUTE_KEY_PREFIX), "A chave de rota (Hash) não foi removida do Redis");

                verify(travelCacheService, times(1)).invalidateTravelStaticCache(travel.getId());
                verify(travelNotificationService, times(1)).sendTravelEndedNotification(any(Travel.class));
                verify(polylineService, times(1)).formattedPolylineEncoded(anyList());
            }

            @Test
            @DisplayName("Deve desvincular todos os estudantes embarcados automaticamente")
            void shouldDeactivateEmbarkedStudentsWhenTravelEnds() throws Exception {
                StudentTravel studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel2 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel3 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel4 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel5 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);

                List<StudentTravel> studentTravels = Arrays.asList(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5);
                studentTravelRepository.saveAll(studentTravels);

                travel.setStudentTravels(Set.of(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5));
                travelRepository.save(travel);

                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel result = travelRepository.findById(travel.getId()).orElseThrow();
                List<StudentTravel> updatedStudentTravels = studentTravelRepository.findAll();

                assertEquals(TravelStatus.FINISH, result.getTravelStatus());
                assertNotNull(result.getEndHourTravel());

                assertEquals(6, updatedStudentTravels.size());
                assertTrue(updatedStudentTravels.stream().noneMatch(StudentTravel::isEmbark));

                assertTrue(updatedStudentTravels.stream().allMatch(st -> st.getDisembarkHour() != null));

                verify(travelCacheService, times(1)).invalidateTravelStaticCache(travel.getId());
                verify(travelNotificationService, times(1)).sendTravelEndedNotification(any(Travel.class));
                verify(polylineService, times(1)).formattedPolylineEncoded(anyList());
            }

            @Test
            @DisplayName("Deve garantir que os estudantes que já estão desembarcados não sofram de nenhuma alteração")
            void shouldKeepStudentsAlreadyDisembarkedUnchanged() throws Exception {
                StudentTravel studentTravel = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel2 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel3 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel4 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel5 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);

                List<StudentTravel> studentTravels = Arrays.asList(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5);
                studentTravelRepository.saveAll(studentTravels);

                travel.setStudentTravels(Set.of(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5));
                travelRepository.save(travel);

                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel result = travelRepository.findById(travel.getId()).orElseThrow();
                List<StudentTravel> updatedStudentTravels = studentTravelRepository.findAll();

                assertEquals(TravelStatus.FINISH, result.getTravelStatus());
                assertNotNull(result.getEndHourTravel());

                assertEquals(6, updatedStudentTravels.size());
                assertTrue(updatedStudentTravels.stream().noneMatch(StudentTravel::isEmbark));
                assertEquals(0, updatedStudentTravels.stream().filter(StudentTravel::isEmbark).count());

                assertTrue(updatedStudentTravels.stream().allMatch(st -> st.getDisembarkHour() != null));
            }

            @Test
            @DisplayName("Deve gerar os relatórios da viagem com sucesso")
            void shouldPersistTravelReportWhenTravelEnds() throws Exception {
                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                List<TravelReports> travelReportsResult = travelReportsRepository.findAll();
                Travel savedTravel = travelRepository.findById(travel.getId()).orElseThrow();
                List<StudentTravel> updatedStudentTravels = studentTravelRepository.findAll();

                assertFalse(travelReportsResult.isEmpty());

                TravelReports report = travelReportsResult.stream()
                        .filter(tr -> tr.getTravel().getId().equals(savedTravel.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Relatório não encontrado para a viagem " + savedTravel.getId()));

                System.out.println("getEndHourTravel" + savedTravel.getEndHourTravel());

                Duration durationInMinutes = Duration.between(savedTravel.getStartHourTravel(), savedTravel.getEndHourTravel());
                double formattedDurationInMinutes = (double) durationInMinutes.toMinutes() / 60.0;

                assertEquals(savedTravel.getId(), report.getTravel().getId());
                assertNotNull(report.getDistanceTraveled());
                assertEquals(formattedDurationInMinutes, report.getDurationInMinutes());
                assertEquals(updatedStudentTravels.size(), report.getBusActualOccupancy());
                assertEquals(updatedStudentTravels.size(), report.getBusActualOccupancy());
                assertEquals(savedTravel.getPolylineRoute(), report.getActualPath());
            }

            @Test
            @DisplayName("Deve calcular corretamente a porcentagem de ocupação do ônibus")
            void shouldCalculateOccupancyMetricsCorrectly() throws Exception {
                StudentTravel studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel2 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel3 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel4 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel5 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel6 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel7 = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel8 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                StudentTravel studentTravel9 = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);

                List<StudentTravel> studentTravels = Arrays.asList(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5, studentTravel6, studentTravel7, studentTravel8, studentTravel9);
                studentTravelRepository.saveAll(studentTravels);

                travel.setStudentTravels(Set.of(studentTravel, studentTravel2, studentTravel3, studentTravel4, studentTravel5));
                travelRepository.save(travel);

                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                List<TravelReports> travelReportsResult = travelReportsRepository.findAll();
                Travel savedTravel = travelRepository.findById(travel.getId()).orElseThrow();
                List<StudentTravel> storageStudentTravels = studentTravelRepository.findAll();

                assertFalse(travelReportsResult.isEmpty());

                TravelReports report = travelReportsResult.stream()
                        .filter(tr -> tr.getTravel().getId().equals(savedTravel.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Relatório não encontrado para a viagem " + savedTravel.getId()));

                assertEquals(10, storageStudentTravels.size());

                assertEquals(80, report.getOccupancyPercentage());
                assertEquals(10, report.getBusExpectedStudents());
                assertEquals(8, report.getBusActualOccupancy());
            }

            @Test
            @DisplayName("Deve validar o fluxo onde não existe registro de polyline no histórico (necessário para viagens curtas sendo canceladas)")
            void shouldEndTravelWithoutRecordedLocationHistory() throws Exception {
                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(null);

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                List<TravelReports> travelReportsResult = travelReportsRepository.findAll();
                Travel travelStorage = travelRepository.findById(travel.getId()).orElseThrow();

                assertFalse(travelReportsResult.isEmpty());

                TravelReports report = travelReportsResult.stream()
                        .filter(tr -> tr.getTravel().getId().equals(travelStorage.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Relatório não encontrado para a viagem " + travelStorage.getId()));

                assertNotNull(report);

                assertEquals(TravelStatus.FINISH, travelStorage.getTravelStatus());
                assertNotNull(travelStorage.getEndHourTravel());

                assertNull(report.getActualPath()); // deve ser salva como null no relatório
            }

            @Test
            @DisplayName("Deve garantir que o histórico temporário da viagem está sendo corretamente apago")
            void shouldDeleteTravelLocationHistoryWhenTravelEnds() throws Exception {
                // getAccumulatedDistance
                redisTemplate.opsForHash().put(ROUTE_KEY_PREFIX, "accumulatedDistance", "300.0");

                // addActiveTravel
                redisTemplate.opsForSet().add(SET_KEY, travel.getId().toString());

                // vários históricos de loc persistidos
                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));
                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));
                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));
                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));
                travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));

                when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(travel.getPolylineRoute());

                mockMvc.perform(post(completePathController)
                                .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                List<TravelLocationHistory> result = travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId());

                assertTrue(result.isEmpty()); // deve retornar vazio pois ja foi deletado
            }
        }

         @Nested
         class failureScenarios {

             @Test
             @DisplayName("Deve lançar exception quando não encontrar a viagem correspondete")
             void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
                 mockMvc.perform(post(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/end")
                                 .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                 .contentType(MediaType.APPLICATION_JSON))
                         .andDo(print())
                         .andExpect(status().isNotFound());

                 verifyNoInteractions(polylineService, travelNotificationService, travelCacheService);
             }

             @ParameterizedTest
             @DisplayName("Deve lançar exception quando a viagem não estiver em andamento")
             @MethodSource("travelStatusProvider")
             void shouldRejectEndWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) throws Exception {
                 travel.setTravelStatus(invalidTravelStatus);
                 travelRepository.save(travel);

                 mockMvc.perform(post(completePathController)
                                 .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                 .contentType(MediaType.APPLICATION_JSON))
                         .andDo(print())
                         .andExpect(status().isConflict());

                 assertEquals(0, travelReportsRepository.count()); // sem relatório criado

                 Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();
                 assertNotEquals(TravelStatus.TRAVELLING, storageTravel.getTravelStatus());

                 verifyNoInteractions(polylineService, travelNotificationService, travelCacheService);
             }

             public static Stream<Arguments> travelStatusProvider() {
                 return Stream.of(
                         Arguments.of(TravelStatus.FINISH),
                         Arguments.of(TravelStatus.CANCELED),
                         Arguments.of(TravelStatus.PENDING)
                 );
             }
         }
    }

    @Nested
    class joinTravel {
        Customer customer;
        City city;
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING,-38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        @DisplayName("Deve validar o cenário completo onde ocorre a vinculação de um estudante")
        void shouldLinkStudentOnTravelWithSuccess() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            List<StudentTravel> studentTravelsList = studentTravelRepository.findAll();

            assertEquals(1, studentTravelsList.size());

            StudentTravel savedLinked = studentTravelsList.get(0);

            assertEquals(travel.getId(), savedLinked.getTravel().getId());
            assertEquals(student.getId(), savedLinked.getStudent().getId());

            assertTrue(savedLinked.isEmbark());
            assertNotNull(savedLinked.getEmbarkHour());
            assertEquals(StudentTravelStatus.ACTIVE ,savedLinked.getStudentTravelStatus());

            assertTrue(savedLinked.getEmbarkHour().isAfter(java.time.Instant.now().minusSeconds(5)));
        }

        @Test
        @DisplayName("Deve invalidar o cache do estudante após realizar a vinculação")
        void shouldEvictStudentTravelCacheAfterJoiningTravel() throws Exception {
            final String TRAVEL_STUDENTS_STATUS_KEY = "travel:students:status:";
            final String TRAVEL_STUDENTS_EMBARK_KEY = "travel:students:embark:";
            final String TRAVEL_STUDENTS_ID_KEY = "travel:students:studentId:";
            final String TRAVEL_STUDENTS_TRAVEL_ID_KEY = "travel:students:studentTravelId:";

            studentTravel = new StudentTravel(null, travel, student, false, null, null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travelStudentStateCacheService.getOrLoadStudentTravelCache(
                    travel.getId(),
                    student.getEmail()
            );

            assertTrue(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_TRAVEL_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertTrue(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertTrue(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_EMBARK_KEY + travel.getId(),
                    student.getEmail()));

            assertTrue(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_STATUS_KEY + travel.getId(),
                    student.getEmail()));

            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            assertFalse(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_TRAVEL_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertFalse(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertFalse(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_EMBARK_KEY + travel.getId(),
                    student.getEmail()));

            assertFalse(redisTemplate.opsForHash().hasKey(
                    TRAVEL_STUDENTS_STATUS_KEY + travel.getId(),
                    student.getEmail()));

            assertNull(redisTemplate.opsForHash().get(
                    TRAVEL_STUDENTS_TRAVEL_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertNull(redisTemplate.opsForHash().get(
                    TRAVEL_STUDENTS_ID_KEY + travel.getId(),
                    student.getEmail()));

            assertNull(redisTemplate.opsForHash().get(
                    TRAVEL_STUDENTS_EMBARK_KEY + travel.getId(),
                    student.getEmail()));

            assertNull(redisTemplate.opsForHash().get(
                    TRAVEL_STUDENTS_STATUS_KEY + travel.getId(),
                    student.getEmail()));
        }

        @ParameterizedTest
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
            travel.setTravelStatus(travelStatus);
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId(), student.getEmail())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH),
                    Arguments.of(TravelStatus.CANCELED)
            );
        }

        @Test
        void throwExceptionWhenStudentAlreadyLinked() throws Exception {
            // vincula estudante à viagem
            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));

            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId(), student.getEmail())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());

            boolean result = travel.getStudentTravels().stream().anyMatch(s -> s.getStudent().getId().equals(student.getId()));

            assertTrue(result);
        }

        @Test
        void throwExceptionWhenStudentNotFound() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId())
                            .with(user("notFoundEmail@gmail.com"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        void throwExceptionTravelIdNotExistsFromDatabase() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/join", UUID.randomUUID())
                            .with(user("notFoundEmail@gmail.com"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Deve lançar exception quando o estudante pertencer a um outro customer")
        void shouldReturnConflictWhenStudentBelongsToDifferentCustomer() throws Exception {
            // pre setup
            City anotherCity = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(anotherCity);

            Customer anotherCustomer = new Customer(null, "Universidade do Oeste", "universidade-oste", "12.315.678/0001-90", true, anotherCity, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(anotherCustomer);

            student.setCustomer(anotherCustomer);
            studentRepository.save(student);

            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isConflict());

            assertEquals(0, studentTravelRepository.count()); // nenhum studentTravel persistido
        }
    }

    @Nested
    class driverChanged {
        Customer customer;
        City city;
        Driver driver;
        Driver driverCandidate;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        private String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            driverCandidate = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 10);
            driverRepository.save(driverCandidate);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING,-38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/change/" + driverCandidate.getId();
        }

        @Test
        @DisplayName("Deve fazer a alteração do motorista com sucesso")
        void shouldChangeTravelDriverWhenRequestIsValid() throws Exception {
            mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(driverCandidate, storageTravel.getDriver());
            assertEquals(TravelStatus.PENDING, storageTravel.getTravelStatus());

            verify(travelNotificationService, times(1)).sendDriverChangedNotification(any(Travel.class), any(Driver.class));
        }

        @Test
        @DisplayName("deve lançar exception quando a viagem não existir")
        void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
            mockMvc.perform(put(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/change/" + driverCandidate.getId()).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }

        @ParameterizedTest
        @DisplayName("deve lançar exception quando a viagem estiver cancelada ou finalizada")
        @MethodSource("travelStatusProvider")
        void shouldReturnConflictWhenTravelIsCanceledOrFinished(TravelStatus travelStatus) throws Exception {
            travel.setTravelStatus(travelStatus);
            travelRepository.save(travel);

            mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isConflict());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.CANCELED),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        @DisplayName("Deve lançar exception quando o motorista não existir")
        void shouldReturnNotFoundWhenDriverDoesNotExist() throws Exception {
            mockMvc.perform(put(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/change/" + UUID.randomUUID()).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }

        @Test
        @DisplayName("Deve lançar exception quando o motorista estiver inativo")
        void shouldReturnConflictWhenDriverIsInactive() throws Exception {
            driverCandidate.setStatus(GeneralStatus.INACTIVE);
            driverRepository.save(driverCandidate);

            mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }

        @Test
        @DisplayName("Deve lançar exception quando o motorista já estiver com outra viagem ativa")
        void shouldReturnConflictWhenDriverAlreadyHasActiveTravel() throws Exception {
            Travel travelTwo = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelTwo.setDriver(driverCandidate);
            travelRepository.save(travelTwo);

            mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isConflict());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }

        @Test
        @DisplayName("Deve lançar exception quando os motoristas pertencerem a um Customer diferente")
        void shouldReturnConflictWhenDriversBelongToDifferentCustomers() throws Exception {
            City anotherCity = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(anotherCity);

            Customer anotherCustomer = new Customer(null, "Universidade do Oeste", "universidade-oeste", "13.345.678/0001-90", true, anotherCity, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(anotherCustomer);

            driverCandidate.setCustomer(anotherCustomer);
            driverRepository.save(driverCandidate);

            mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isConflict());

            Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(storageTravel.getDriver(), driver); // driver não muda
        }
    }

    @Nested
    class cancelTravel {
        Customer customer;
        City city;
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        private String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.PENDING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING,-38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/cancel";
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve que uma viagem pendente seja corretamente cancelada")
            void shouldCancelPendingTravelSuccessfully() throws Exception {
                mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

                assertEquals(TravelStatus.CANCELED, storageTravel.getTravelStatus());
                assertNotNull(storageTravel.getEndHourTravel());

                verify(travelNotificationService, times(1)).sendTravelCanceledNotification(any(Travel.class));

            }

            @Test
            @DisplayName("Deve cancelar a viagem disvinculando os estudantes embarcados")
            void shouldDisconnectEmbarkedStudentsWhenCancelingTravel() throws Exception {
                studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
                studentTravelRepository.save(studentTravel);

                mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

                assertEquals(TravelStatus.CANCELED, storageTravel.getTravelStatus());

                List<StudentTravel> result = studentTravelRepository.findAll();

                assertEquals(1, studentTravelRepository.count());
                assertTrue(result.stream().noneMatch(StudentTravel::isEmbark));
                assertNotNull(result.getFirst().getDisembarkHour());
            }

            @Test
            @DisplayName("Deve cancelar a viagem mantendo os estudantes desembarcagos inalterados")
            void shouldKeepAlreadyDisconnectedStudentsUnchangedWhenCancelingTravel() throws Exception {
                studentTravel = new StudentTravel(null, travel, student, false, Instant.now().minusSeconds(20), Instant.now(), null, StudentTravelStatus.ACTIVE);
                studentTravelRepository.save(studentTravel);

                mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

                assertEquals(TravelStatus.CANCELED, storageTravel.getTravelStatus());

                List<StudentTravel> result = studentTravelRepository.findAll();

                assertEquals(1, studentTravelRepository.count());
                assertTrue(result.stream().noneMatch(StudentTravel::isEmbark));
                assertNotNull(result.getFirst().getDisembarkHour());
            }
        }

        @Nested
        class failureScenarios {

            @Test
            void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
                mockMvc.perform(put(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/cancel").with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound());

                Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

                assertNotEquals(TravelStatus.CANCELED, storageTravel.getTravelStatus()); // não deve mudar status
                assertNull(storageTravel.getEndHourTravel()); // nao deve setar hora de encerramento
            }

            @ParameterizedTest
            @MethodSource("travelStatusProvider")
            void shouldReturnConflictWhenTravelHasAnotherTravelStatus(TravelStatus invalidTravelStatus) throws Exception {
                travel.setTravelStatus(invalidTravelStatus);
                travelRepository.save(travel);

                mockMvc.perform(put(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isConflict());

                Travel storageTravel = travelRepository.findById(travel.getId()).orElseThrow();

                assertNotEquals(TravelStatus.CANCELED, storageTravel.getTravelStatus()); // não deve mudar status
                assertNull(storageTravel.getEndHourTravel()); // nao deve setar hora de encerramento
            }

            public static Stream<Arguments> travelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.TRAVELLING),
                        Arguments.of(TravelStatus.FINISH)
                );
            }

        }
    }

    @Nested
    class leaveTravel {
        Customer customer;
        City city;
        Driver driver;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        TravelRequestDTO travelRequestDTO;
        TravelCacheDTO travelCacheDTO;
        StudentTravelCacheDTO studentTravelCacheDTO;

        private String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            studentTravel = new StudentTravel(null, travel, student, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), TravelPeriod.MORNING,-38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
            travelCacheDTO = new TravelCacheDTO(travel.getId(), travel.getTravelStatus(), -38.501200, -12.971800, "", 5500.0, 120.0);
            studentTravelCacheDTO = new StudentTravelCacheDTO(studentTravel.getId(), student.getEmail(), student.getId(), StudentTravelStatus.ACTIVE, true);

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/leave";
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve garantir que um estudante ativo consiga sair de uma viagem em andamento.")
            void shouldDisconnectStudentFromTravelSuccessfully() throws Exception {
                when(travelCacheService.getOrLoadTravelStaticCache(eq(travel.getId()))).thenReturn(travelCacheDTO);
                when(travelStudentStateCacheService.getOrLoadStudentTravelCache(eq(travel.getId()), anyString()))
                        .thenReturn(studentTravelCacheDTO);

                mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                StudentTravel result = studentTravelRepository.findAll().getFirst();

                assertFalse(result.isEmbark());
                assertNotNull(result.getDisembarkHour());
                assertEquals(StudentTravelStatus.LEFT, result.getStudentTravelStatus());
            }

            @Test
            @DisplayName("Deve garantir que os dados do estudante sejam removidos do Redis após o desligamento da viagem")
            void shouldEvictStudentCacheAfterLeavingTravel() throws Exception {
                when(travelCacheService.getOrLoadTravelStaticCache(eq(travel.getId()))).thenReturn(travelCacheDTO);
                when(travelStudentStateCacheService.getOrLoadStudentTravelCache(eq(travel.getId()), anyString()))
                        .thenReturn(studentTravelCacheDTO);

                mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNoContent());

                verify(travelStudentStateCacheService, times(1)).evictStudentTravelCachedData(travel.getId(), student.getEmail());
            }
        }
        
        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve garantir que apenas viagens em andamento permitam o desligamento.")
            @MethodSource("travelStatusProvider")
            void shouldReturnConflictWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) throws Exception {
                TravelCacheDTO travelCacheWithInvalidStatus = new TravelCacheDTO(travel.getId(), invalidTravelStatus, -38.501200, -12.971800, "", 5500.0, 120.0);

                when(travelCacheService.getOrLoadTravelStaticCache(eq(travel.getId()))).thenReturn(travelCacheWithInvalidStatus);
                when(travelStudentStateCacheService.getOrLoadStudentTravelCache(eq(travel.getId()), anyString()))
                        .thenReturn(studentTravelCacheDTO);

                mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isConflict());

                StudentTravel result = studentTravelRepository.findAll().getFirst();

                // verificação aposta: nada deve ter sido alterado
                assertTrue(result.isEmbark());
                assertNull(result.getDisembarkHour());
                assertNotEquals(StudentTravelStatus.LEFT, result.getStudentTravelStatus());
            }

            public static Stream<Arguments> travelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.CANCELED),
                        Arguments.of(TravelStatus.PENDING),
                        Arguments.of(TravelStatus.FINISH)
                );
            }

            @Test
            @DisplayName("Deve garantir que viagens inexistentes não permitam o desligamento do estudante.")
            void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
                when(travelCacheService.getOrLoadTravelStaticCache(eq(travel.getId()))).thenThrow(new EntityNotFoundException());

                mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound());

                StudentTravel result = studentTravelRepository.findAll().getFirst();

                // verificação aposta: nada deve ter sido alterado
                assertTrue(result.isEmbark());
                assertNull(result.getDisembarkHour());
                assertNotEquals(StudentTravelStatus.LEFT, result.getStudentTravelStatus());
            }

            @Test
            @DisplayName("Deve garantir que apenas estudantes ativos possam sair da viagem")
            void shouldReturnConflictWhenStudentIsNotEmbarked() throws Exception {
                StudentTravelCacheDTO newStudentTravelCacheDTO = new StudentTravelCacheDTO(studentTravel.getId(), student.getEmail(), student.getId(), StudentTravelStatus.ACTIVE, false);

                when(travelCacheService.getOrLoadTravelStaticCache(eq(travel.getId()))).thenReturn(travelCacheDTO);
                when(travelStudentStateCacheService.getOrLoadStudentTravelCache(eq(travel.getId()), anyString()))
                        .thenReturn(newStudentTravelCacheDTO);

                mockMvc.perform(post(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isNotFound());

                StudentTravel result = studentTravelRepository.findAll().getFirst();

                // verificação aposta: nada deve ter sido alterado
                assertTrue(result.isEmbark());
                assertNull(result.getDisembarkHour());
                assertNotEquals(StudentTravelStatus.LEFT, result.getStudentTravelStatus());
            }
        }
    }

    @Nested
    class getTravelPreview {
        Customer customer;
        City city;
        Travel travel;
        Student student;

        private String completePathController;

        @BeforeEach
        void setUp() {
            city = new City(null, "feira de santana", CitySize.CITY, true);
            cityRepository.save(city);

            customer = new Customer(null, "Universidade do Centro", "universidade-centro", "12.345.678/0001-90", true, city, ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
            customerRepository.save(customer);

            Driver driver = new Driver(null, "rafael.silva@test.com", "Senha@123", "Rafael", "Silva", "11999998888", "drivers/rafael-silva.jpg", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 15, 10, 30), null, customer, "CITY", 12);
            driverRepository.save(driver);

            travel = new Travel(null, TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:05:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
            travelRepository.save(travel);

            student = new Student(null, "email@exemplo.com", "senha123", "studentName", "studentLastName", "11999999999", "perfil.png", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");
            studentRepository.save(student);

            completePathController = PATH_CONTROLLER + "/" + travel.getId() + "/preview";
        }

        @Test
        @DisplayName("Deve garantir que o endpoint retorne corretamente os dados de preview (com viagem já iniciada)")
        void shouldReturnTravelPreviewWithEstimatedArrivalTime() throws Exception {
            MockHttpServletResponse response = mockMvc.perform(get(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk()).andReturn().getResponse();

            assertThat(response.getContentAsString().contains("distance"));
            assertThat(response.getContentAsString().contains("duration"));
            assertThat(response.getContentAsString().contains("destinationCity"));
            assertThat(response.getContentAsString().contains("arrivalTime"));
        }

        @Test
        @DisplayName("Deve garantir que viagens ainda não inicializadas retornem o preview corretamente sem horário previsto")
        void shouldReturnTravelPreviewWithoutArrivalTimeWhenTravelHasNotStarted() throws Exception {
            travel.setTravelStatus(TravelStatus.PENDING);
            travel.setStartHourTravel(null);
            travelRepository.save(travel);

            MockHttpServletResponse response = mockMvc.perform(get(completePathController).with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk()).andReturn().getResponse();

            assertThat(response.getContentAsString().contains("distance"));
            assertThat(response.getContentAsString().contains("duration"));
            assertThat(response.getContentAsString().contains("destinationCity"));
            assertThat( ! (response.getContentAsString().contains("arrivalTime") )); // sem arrivalTime
        }

        @Test
        @DisplayName("Deve lançar exception quando a viagem não existir")
        void shouldReturnNotFoundWhenTravelDoesNotExist() throws Exception {
            MockHttpServletResponse response = mockMvc.perform(
                            get(PATH_CONTROLLER + "/" + UUID.randomUUID() + "/preview")
                                    .with(user(AUTH_USER).authorities(new SimpleGrantedAuthority("ROLE_DRIVER")))
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andReturn()
                    .getResponse();

            TravelPreviewDTO result = objectMapper.readValue(response.getContentAsString(), TravelPreviewDTO.class);

            assertNull(result.distance());
            assertNull(result.duration());
            assertNull(result.destinationCity());
            assertNull(result.arrivalTime());
        }


    }
}
