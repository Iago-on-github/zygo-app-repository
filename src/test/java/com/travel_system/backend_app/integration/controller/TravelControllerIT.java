package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.MapboxAPIService;
import com.travel_system.backend_app.service.RedisTrackingService;
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
import org.testcontainers.shaded.org.checkerframework.framework.qual.DefaultQualifierForUse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private MapboxAPIService mapboxAPIService;
    @Autowired
    private RedisTrackingService redisTrackingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

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
        travelLocationHistoryRepository.deleteAll();
    }

    @Nested
    class createTravel {
        Driver driver;
        VehicleLocationRequestDTO requestDTO;
        TravelRequestDTO travelRequestDTO;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), null);
            driverRepository.save(driver);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        @DisplayName("should create a new travel with success")
        void shouldCreateNewTravelWithSuccess() throws Exception {
            when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new TravelPreviewDTO(600.0, 40.2, "Feira de Santana", "123"));

            mockMvc.perform(post("/v1/travel/create")
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.driverResponseDTO.id").value(driver.getId().toString()));

            List<Travel> travels = travelRepository.findAll();

            Travel firstTravelSaved = travels.getFirst();
            assertEquals(TravelStatus.PENDING, firstTravelSaved.getTravelStatus());
            assertEquals(driver.getId(), firstTravelSaved.getDriver().getId());
            assertEquals(600.0, firstTravelSaved.getDistance());
            assertEquals(40.2, firstTravelSaved.getDuration());
            assertEquals("Feira de Santana", firstTravelSaved.getDestinationCity());

            assertNotNull(firstTravelSaved.getCreatedAt());
        }

        @Test
        void throwExceptionWhenDriverIsInactive() throws Exception {
            driver.setStatus(GeneralStatus.INACTIVE);
            driverRepository.save(driver);

            mockMvc.perform(post("/v1/travel/create")
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isBadRequest());

        }

        @Test
        @DisplayName("throw exception when Travel has TravelStatus 'PENDING' or 'TRAVELLING' ")
        void throwExceptionWhenTravelAlreadyUnderway() throws Exception {
            Travel travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/create")
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isConflict());

            boolean result = travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING));

            assertTrue(result);
        }
    }

    @Nested
    class startTravel {
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), null);
            driverRepository.save(driver);

             travel = new Travel(
                    null, null, TravelStatus.PENDING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        void shouldStartTravelWithSuccess() throws Exception {
            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new RouteDetailsDTO(35.3, 2034.3, "encoded_geometry_response"));

            mockMvc.perform(post("/v1/travel/{travelId}/start", travel.getId())
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            Travel result = travelRepository.findById(travel.getId()).orElseThrow();

            assertEquals(TravelStatus.TRAVELLING, result.getTravelStatus());
            assertEquals(35.3, result.getDuration());
            assertEquals("encoded_geometry_response", result.getPolylineRoute());
            assertNotNull(result.getStartHourTravel());
        }

        @Test
        @DisplayName("should persist route data for self-health of the system")
        void shouldPersistRouteDataWithSuccess() throws Exception {
            String key = "ACTIVE_TRAVELS_KEY";

            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new RouteDetailsDTO(07.2, 304.2, "encoded_geometry"));

            mockMvc.perform(post("/v1/travel/{travelId}/start", travel.getId())
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isNoContent());

            SetOperations<String, String> hashSet = redisTemplate.opsForSet();

            assertTrue(redisTemplate.opsForSet().members(key).contains(travel.getId().toString()));
            assertEquals(2, hashSet.size(key));
        }

        @ParameterizedTest
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
            travel.setTravelStatus(travelStatus);
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/start", travel.getId())
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isConflict());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.TRAVELLING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @ParameterizedTest
        @MethodSource("routeDetailsProvider")
        void throwExceptionWhenTheMapboxApiReturnsInvalidResponse(RouteDetailsDTO routeDetailsDTO) throws Exception {
            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(routeDetailsDTO);

            mockMvc.perform(post("/v1/travel/{travelId}/start", travel.getId())
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isBadGateway());
        }

        public static Stream<Arguments> routeDetailsProvider() {
            return Stream.of(
                    Arguments.of(new RouteDetailsDTO(null, 300.3, "encoded_geometry")),
                    Arguments.of(new RouteDetailsDTO(07.2, null, "encoded_geometry")),
                    Arguments.of(new RouteDetailsDTO(07.2, 300.3, null)),
                    Arguments.of((RouteDetailsDTO) null)
            );
        }


    }

    @Nested
    class endTravel {
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), null);
            driverRepository.save(driver);

            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);

            studentTravel = new StudentTravel(null, travel, null, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        @DisplayName("Should end travel with success, generate report and clear caches")
        void shouldEndTravelWithSuccess() throws Exception {
            String activeTravelsKey = "ACTIVE_TRAVELS_KEY";

            String trackingKey = "travel:tracking:" + travel.getId();
            String routeKey = "travel:route:" + travel.getId();

            travel.setStartHourTravel(Instant.now().minus(30, java.time.temporal.ChronoUnit.MINUTES));
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travelRepository.save(travel);

            redisTemplate.opsForSet().add(activeTravelsKey, travel.getId().toString());
            redisTemplate.opsForHash().put(routeKey, "last_calc_lat", "-12.97");
            redisTemplate.opsForValue().set("travel:distance:" + travel.getId(), "1500.0"); // Se o cache de distância usar essa chave

            travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));

            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
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

            assertFalse(redisTemplate.opsForSet().isMember(activeTravelsKey, travel.getId().toString()),
                    "A viagem ainda consta no Set de viagens ativas no Redis");
            assertFalse(redisTemplate.hasKey(routeKey), "A chave de rota (Hash) não foi removida do Redis");
            assertFalse(redisTemplate.hasKey(trackingKey), "A chave de tracking não foi removida do Redis");
        }

        @Test
        void shouldDisembarkStudentsLinkedWithSuccess() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            StudentTravel studentTravelResult = studentTravelRepository.findById(studentTravel.getId()).orElseThrow();

            assertFalse(studentTravelResult.isEmbark());
            assertNotNull(studentTravelResult.getDisembarkHour());
        }

        @Test
        @DisplayName("should metrics generate for travelReports with success")
        void shouldMetricsGenerateCorrectWithSuccess() throws Exception {
            // viagem começou 1h atrás
            travel.setStartHourTravel(Instant.now().minus(Duration.ofHours(1)));
            travelRepository.save(travel);

            String routeKey = "travel:route:" + travel.getId();

            // getAccumulatedDistance
            redisTemplate.opsForHash().put(routeKey, "accumulatedDistance", "30.6");

            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            TravelReports travelReportsResult = travelReportsRepository.findAll().stream()
                    .filter(tr -> tr.getTravel().getId().equals(travel.getId()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(30.6, travelReportsResult.getDistanceTraveled());

            assertEquals(1.0, travelReportsResult.getDurationInMinutes(), 0.1);

            assertEquals(1, travelReportsResult.getBusExpectedStudents());
            assertEquals(1, travelReportsResult.getBusActualOccupancy());
            assertEquals(100, travelReportsResult.getOccupancyPercentage());

            assertNotNull(travelReportsResult.getGeneratedAt());
        }

        @Test
        void shouldGeneratePolylineWithSuccess() throws Exception {
            // cria dados para usar no teste
            List<TravelLocationHistory> history = List.of(
                    new TravelLocationHistory(travel.getId(), null, -12.9714, -38.5016, Instant.now().minusSeconds(10)),
                    new TravelLocationHistory(travel.getId(), null, -12.9710, -38.5010, Instant.now().minusSeconds(5))
            );
            travelLocationHistoryRepository.saveAll(history);

            when(polylineService.formattedPolylineEncoded(any())).thenReturn("new_encoded_polyline");

            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            TravelReports report = travelReportsRepository.findAll().stream()
                    .filter(r -> r.getTravel().getId().equals(travel.getId()))
                    .findFirst()
                    .orElseThrow();

            assertEquals("new_encoded_polyline", report.getActualPath());

            // deve limpar após encerramento da viagem
            assertTrue(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()).isEmpty());
        }

        @Test
        void shouldPermitPolylineNullOrBlankWithSuccess() throws Exception {
            // cria dados para usar no teste
            List<TravelLocationHistory> history = List.of(
                    new TravelLocationHistory(travel.getId(), null, -12.9714, -38.5016, Instant.now().minusSeconds(10)),
                    new TravelLocationHistory(travel.getId(), null, -12.9710, -38.5010, Instant.now().minusSeconds(5))
            );
            travelLocationHistoryRepository.saveAll(history);

            when(polylineService.formattedPolylineEncoded(any())).thenReturn(null);

            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            TravelReports report = travelReportsRepository.findAll().stream()
                    .filter(r -> r.getTravel().getId().equals(travel.getId()))
                    .findFirst()
                    .orElseThrow();

            assertNull(report.getActualPath());

            // deve limpar após encerramento da viagem
            assertTrue(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()).isEmpty());
        }

        // failure scenarios

        @ParameterizedTest
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
            travel.setTravelStatus(travelStatus);
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/end", travel.getId())
                            .with(user("authenticated_user"))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        void throwExceptionWhenTravelNotFound() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/end", UUID.randomUUID())
                            .with(user("authenticated_user"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class joinTravel {
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), null);

            driverRepository.save(driver);

            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);

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

            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        @DisplayName("should save embark=true and embarkHour with success")
        void shouldLinkStudentOnTravelWithSuccess() throws Exception {
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/join", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            List<StudentTravel> studentTravelsList = studentTravelRepository.findAll();

            assertEquals(1, studentTravelsList.size());

            StudentTravel savedLink = studentTravelsList.get(0);

            assertEquals(travel.getId(), savedLink.getTravel().getId(), "O vínculo não foi associado à viagem correta");
            assertEquals(student.getId(), savedLink.getStudent().getId(), "O vínculo não foi associado ao estudante correto");

            assertTrue(savedLink.isEmbark(), "O campo embark deveria ter sido salvo como true");
            assertNotNull(savedLink.getEmbarkHour(), "O campo embarkHour deveria conter o timestamp do embarque");

            assertTrue(savedLink.getEmbarkHour().isAfter(java.time.Instant.now().minusSeconds(5)),
                    "O embarkHour gravado está fora da janela de tempo aceitável");
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
                    Arguments.of(TravelStatus.FINISH)
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

    }

    @Nested
    class leaveTravel {
        Driver driver;
        TravelRequestDTO travelRequestDTO;
        Travel travel;
        StudentTravel studentTravel;
        Student student;

        @BeforeEach
        void setUp() {
            driver = new Driver(
                    null, "driver@test.com", "encoded_pass",
                    "João", "Silva", "71999999999",
                    null, GeneralStatus.ACTIVE,
                    LocalDateTime.now(), LocalDateTime.now(),
                    "Salvador", 0, new ArrayList<>(), null);
            driverRepository.save(driver);

            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );
            travelRepository.save(travel);

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

            travelRepository.save(travel);

            // vincula estudante à viagem
            studentTravel = new StudentTravel(null, travel, student, true, Instant.now().minusSeconds(20), null, null, StudentTravelStatus.ACTIVE);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400, "Feira de Santana");
        }

        @Test
        void shouldLeaveStudentOnWithWithSuccess() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/leave", travel.getId())
                            .with(user(student.getEmail()))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            StudentTravel result = studentTravelRepository.findByTravelIdAndStudentId(travel.getId(), student.getId()).orElseThrow();

            assertEquals(StudentTravelStatus.LEFT, result.getStudentTravelStatus());

            assertFalse(result.isEmbark());
            assertNotNull(result.getDisembarkHour());

            assertTrue(result.getDisembarkHour().isAfter(Instant.now().minusSeconds(5)),
                    "O disembarkHour registrado está fora da janela de tempo aceitável");
        }

        @ParameterizedTest
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) throws Exception {
            travel.setTravelStatus(travelStatus);
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/leave", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        void throwExceptionWhenStudentIsNotActiveOnTrip() throws Exception {
            // cria estudante que nao possui vínculo com a viagem
            Student otherStudent = new Student(
                    null, "outro@gmail.com", "senha", "Maria", "Silva", "71988888888",
                    null, GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(),
                    InstitutionType.UNIVERSITY, "Direito"
            );
            studentRepository.save(otherStudent);

            studentTravel.setStudent(otherStudent);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.save(travel);

            mockMvc.perform(post("/v1/travel/{travelId}/leave", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        void throwExceptionWhenTravelIdNotExists() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/leave", UUID.randomUUID())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        void throwExceptionWhenStudentNotFoundFromDatabase() throws Exception {
            mockMvc.perform(post("/v1/travel/{travelId}/leave", travel.getId())
                            .with(user("NotFoundEmail@gmail.com"))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

    }

    @Nested
    class getTravelPreview {
        Travel travel;
        Student student;

        @BeforeEach
        void setUp() {
            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, null, Instant.now(),
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000, "Feira de Santana"
            );

            travelRepository.save(travel);

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
        }

        @Test
        void shouldGetPreviewTravelWithSuccess() throws Exception {
            mockMvc.perform(get(("/v1/travel/{travelId}/preview"), travel.getId())
                    .with(user(student.getEmail()))
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(jsonPath("$.distance").value(15000.0))
                    .andExpect(jsonPath("$.duration").value(3600.0))
                    .andExpect(jsonPath("$.destinationCity").value("Feira de Santana"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("não deve realizar cálculo do 'arrivalTime' caso os dados de incio da viagem sejam insuficientes")
        void shouldNotCalculateArrivalTimeIfStartTravelDataAreInsufficient() throws Exception {
            travel.setStartHourTravel(null);
            travelRepository.save(travel);

            mockMvc.perform(get("/v1/travel/{travelId}/preview", travel.getId())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.distance").value(15000.0))
                    .andExpect(jsonPath("$.duration").value(3600.0))
                    .andExpect(jsonPath("$.destinationCity").value("Feira de Santana"))
                    .andExpect(jsonPath("$.arrivalTime").doesNotExist());
        }

        @Test
        void throwExceptionWhenTravelNotFound() throws Exception {
            mockMvc.perform(get("/v1/travel/{travelId}/preview", UUID.randomUUID())
                            .with(user(student.getEmail()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }
}
