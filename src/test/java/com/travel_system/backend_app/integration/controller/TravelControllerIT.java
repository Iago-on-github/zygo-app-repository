package com.travel_system.backend_app.integration.controller;

import com.travel_system.backend_app.integration.IntegrationTestBase;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import com.travel_system.backend_app.service.MapboxAPIService;
import com.travel_system.backend_app.service.PolylineService;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MapboxAPIService mapboxAPIService;
    @Autowired
    private RedisTrackingService redisTrackingService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
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
                    "Salvador", 0, new ArrayList<>());
            driverRepository.save(driver);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400);
        }

        @Test
        @DisplayName("should create a new travel with success")
        void shouldCreateNewTravelWithSuccess() throws Exception {
            mockMvc.perform(post("/travel/create")
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
            assertNotNull(firstTravelSaved.getStartHourTravel());
        }

        @Test
        void throwExceptionWhenDriverIsInactive() throws Exception {
            driver.setStatus(GeneralStatus.INACTIVE);
            driverRepository.save(driver);

            mockMvc.perform(post("/travel/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(travelRequestDTO)))
                    .andExpect(status().isBadRequest());

        }

        @Test
        @DisplayName("throw exception when Travel has TravelStatus 'PENDING' or 'TRAVELLING' ")
        void throwExceptionWhenTravelAlreadyUnderway() throws Exception {
            Travel travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);

            mockMvc.perform(post("/travel/create")
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
                    "Salvador", 0, new ArrayList<>());
            driverRepository.save(driver);

             travel = new Travel(
                    null, null, TravelStatus.PENDING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400);
        }

        @Test
        void shouldStartTravelWithSuccess() throws Exception {
            when(mapboxAPIService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(new RouteDetailsDTO(35.3, 2034.3, "encoded_geometry_response"));

            mockMvc.perform(post("/travel/start/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/start/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/start/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/start/{travelId}", travel.getId())
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
                    "Salvador", 0, new ArrayList<>());
            driverRepository.save(driver);

            travel = new Travel(
                    null, null, TravelStatus.TRAVELLING, driver,
                    Instant.now(), null, "~shnC~_rcL_@v@m@p@y@r@",
                    3600.0, 15000.0,
                    -12.9714, -38.5016,
                    -12.8000, -38.4000
            );
            travelRepository.save(travel);

            studentTravel = new StudentTravel(null, travel, null, true, Instant.now().minusSeconds(20), null, null);
            studentTravelRepository.save(studentTravel);

            travel.setStudentTravels(Set.of(studentTravel));
            travelRepository.save(travel);

            travelRequestDTO = new TravelRequestDTO(driver.getId(), -38.501200, -12.971800, -38.482300, -12.950400);
        }

        @Test
        void shouldEndTravelWithSuccess() throws Exception {
            String key = "ACTIVE_TRAVELS_KEY";
            String hashKey = "travelId:" + travel.getId();

            // popula redis para o clearTravelLocationCache
            redisTemplate.opsForSet().add(key, travel.getId().toString());
            redisTemplate.opsForHash().put(hashKey, "last_calc_lat", "-12.97");

            // salva travelLocHistory para verificar deleção posterior
            travelLocationHistoryRepository.save(new TravelLocationHistory(travel.getId(), null, -12.97, -38.50, Instant.now()));

            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            Travel result = travelRepository.findById(travel.getId()).orElseThrow();
            List<TravelReports> travelReports = travelReportsRepository.findAll();

            assertEquals(TravelStatus.FINISH, result.getTravelStatus());
            assertNotNull(result.getEndHourTravel());

            assertFalse(redisTemplate.opsForSet().isMember(key, travel.getId().toString()));
            assertFalse(redisTemplate.hasKey(hashKey));

            assertEquals(1, travelReports.size());
            assertTrue(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()).isEmpty());
        }

        @Test
        void shouldDisembarkStudentsLinkedWithSuccess() throws Exception {
            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
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

            String key = "travelId:" + travel.getId();

            // getAccumulatedDistance
            redisTemplate.opsForHash().put(key, "accumulatedDistance", "30.6");

            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
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

            mockMvc.perform(post("/travel/end/{travelId}", travel.getId())
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
            mockMvc.perform(post("/travel/end/{travelId}", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

}
