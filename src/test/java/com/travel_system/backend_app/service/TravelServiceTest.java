package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     *
     */
    @Mock
    private StudentTravelRepository studentTravelRepository;
    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private MapboxAPIService mapboxAPIService;
    @Mock
    private PolylineService polylineService;

    @Mock
    private TravelReportsRepository travelReportsRepository;
    @Mock
    private TravelRepository travelRepository;
    @Mock
    private DriverRepository driverRepository;
    @Mock
    private TravelLocationHistoryRepository travelLocationHistoryRepository;
    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private TravelService travelService;

    private final ArgumentCaptor<TravelReports> travelReportsCaptor = ArgumentCaptor.forClass(TravelReports.class);

    TravelRequestDTO travelRequestDTO;
    Driver driver;
    Travel travel;
    Student student;
    StudentTravel studentTravel;

    @BeforeEach
    void setUp() {
        travel = new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.PENDING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>()), Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345);

        travelRequestDTO = new TravelRequestDTO(UUID.randomUUID(), -38.501234, -12.973456, -38.512345, -12.985678);

        driver = new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>());

        student = new Student(UUID.randomUUID(), "student@gmail.com", "123456", "Maria", "Oliveira", "75988888888", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), InstitutionType.UNIVERSITY, "Computer Science");

        studentTravel = new StudentTravel(UUID.randomUUID(), new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.TRAVELLING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>()), Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345),
                new Student(), true, Instant.now(), null, new GeoPosition(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), null));

    }

    @Nested
    class createTravel {

        @Test
        @DisplayName("should create travel with success")
        void shouldCreateTravelWithSuccess() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));

            when(travelRepository.save(any(Travel.class))).thenReturn(new Travel());

            TravelResponseDTO result = travelService.createTravel(travelRequestDTO);

            assertNotNull(result);

            ArgumentCaptor<Travel> travelCaptor = ArgumentCaptor.forClass(Travel.class);

            verify(travelRepository, times(1)).save(travelCaptor.capture());
            Travel storedValue = travelCaptor.getValue();

            assertEquals(travelRequestDTO.originLatitude(), storedValue.getOriginLatitude());
            assertEquals(travelRequestDTO.originLongitude(), storedValue.getOriginLongitude());

            assertEquals(travelRequestDTO.finalLatitude(), storedValue.getFinalLatitude());
            assertEquals(travelRequestDTO.finalLongitude(), storedValue.getFinalLongitude());

            assertEquals(TravelStatus.PENDING, storedValue.getTravelStatus());

            assertEquals(driver, storedValue.getDriver());

            assertNotNull(storedValue.getStartHourTravel());
        }

        @Test
        @DisplayName("throw exception when driver not found from database")
        void throwExceptionWhenDriverNotFound() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> travelService.createTravel(travelRequestDTO));

            verify(travelRepository, never()).existsByDriverIdAndTravelStatusIn(any(), anyList());

            verify(travelRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when driver has inactive status")
        void throwExceptionWhenDriverHasInactiveStatus() {
            driver.setStatus(GeneralStatus.INACTIVE);

            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));

            assertThrows(InactiveAccountModificationException.class, () -> travelService.createTravel(travelRequestDTO));

            verify(travelRepository, never()).existsByDriverIdAndTravelStatusIn(any(), anyList());

            verify(travelRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when driver has active travel")
        void ThrowExceptionWhenDriverHasActiveTravel() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));

            when(travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING)))
                    .thenReturn(true);

            assertThrows(TravelException.class, () -> travelService.createTravel(travelRequestDTO));

            verify(driverRepository, times(1)).findById(any());
            verify(travelRepository, times(1)).existsByDriverIdAndTravelStatusIn(any(), anyList());

            verify(travelRepository, never()).save(any());
        }
    }

    @Nested
    class startTravel {

        @Test
        @DisplayName("should start travel with success")
        void shouldStartTravelWithSuccess() {
            RouteDetailsDTO routeDetailsDTO = new RouteDetailsDTO(3600.0, 15.5, "encoded_polyline_example");

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(mapboxAPIService.calculateRoute(travel.getOriginLongitude(), travel.getOriginLatitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);
            when(travelRepository.save(any(Travel.class))).thenReturn(travel);

            travelService.startTravel(travel.getId());

            ArgumentCaptor<Travel> travelCaptor = ArgumentCaptor.forClass(Travel.class);

            verify(travelRepository, times(1)).save(travelCaptor.capture());
            Travel storedValue = travelCaptor.getValue();

            assertAll(
                    () -> assertEquals(travel.getDuration(), storedValue.getDuration()),
                    () -> assertEquals(travel.getDistance(), storedValue.getDistance()),
                    () -> assertEquals(travel.getPolylineRoute(), storedValue.getPolylineRoute()),
                    () -> assertNotNull(storedValue.getStartHourTravel()),
                    () -> assertEquals(TravelStatus.TRAVELLING, storedValue.getTravelStatus())
            );

            verify(redisTrackingService, times(1)).addActiveTravel(any());
        }

        @Test
        @DisplayName("throw exception when travel not found from database")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

        @Test
        @DisplayName("throw exception when travel status is finished")
        void throwExceptionWhenTravelStatusIsFinished() {
            travel.setTravelStatus(TravelStatus.FINISH);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

        @Test
        @DisplayName("throw exception when travel status is travelling")
        void throwExceptionWhenTravelStatusIsTravelling() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

        @Test
        @DisplayName("throw exception when mapbox api returns null from route details")
        void throwExceptionWhenMapBoxAPIReturnsNullFromRouteDetails() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(mapboxAPIService.calculateRoute(travel.getOriginLongitude(), travel.getOriginLatitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(null);

            assertThrows(RecalculateEtaException.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());
            verify(mapboxAPIService, times(1)).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());

            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

    }

    @Nested
    class endTravel {
        @DisplayName("should generate metrics to Travel Reports with success")
        @Test
        void shouldGenerateFullTravelReportWithSuccess() {
            Travel travel = new Travel();

            String polylineRoute = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now().minusSeconds(200), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null)
            );

            List<TravelLocationHistory> locationHistories = List.of(new TravelLocationHistory());

            travel.setStudentTravels(studentTravels);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn(String.valueOf(1500.0));
            when(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()))
                    .thenReturn(locationHistories);
            when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(polylineRoute);

            // act
            travelService.endTravel(travel.getId());

            // assert
            assertEquals(TravelStatus.FINISH, travel.getTravelStatus());
            assertNotNull(travel.getEndHourTravel());

            int remainder = (2 * 100) / 3;
            verify(travelReportsRepository, times(1)).save(travelReportsCaptor.capture());
            assertEquals(3, travelReportsCaptor.getValue().getBusExpectedStudents());
            assertEquals(2, travelReportsCaptor.getValue().getBusActualOccupancy());
            assertEquals(remainder, travelReportsCaptor.getValue().getOccupancyPercentage());

            assertEquals(1500.0, travelReportsCaptor.getValue().getDistanceTraveled());
            assertTrue(travelReportsCaptor.getValue().getDurationInMinutes() > 0);

            verify(redisTrackingService, times(1)).clearTravelLocationCache(any());
        }

        @Test
        @DisplayName("should validate the exactly percentual of occupancy")
        void shouldGeneratePartialOccupancyReport() {
            Travel travel = new Travel();

            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null)
            );

            travel.setStudentTravels(studentTravels);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn("100.0");

            travelService.endTravel(travel.getId());

            verify(travelReportsRepository, times(1)).save(travelReportsCaptor.capture());

            assertEquals(10, travelReportsCaptor.getValue().getBusExpectedStudents());
            assertEquals(5, travelReportsCaptor.getValue().getBusActualOccupancy());
            assertEquals(50, travelReportsCaptor.getValue().getOccupancyPercentage());

            assertTrue(travel.getStudentTravels().stream().noneMatch(StudentTravel::isEmbark));
        }

        @Test
        @DisplayName("should rollback if an error occurs and keep travel status unchanged")
        void shouldRollbackWhenTravelReportsSaveFails() {
            Travel travel = new Travel();

            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now());

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now().minusSeconds(200), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null)
            );

            travel.setStudentTravels(studentTravels);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            doThrow(RuntimeException.class).when(travelReportsRepository).save(any());
            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn("100.0");

            assertThrows(RuntimeException.class, () -> {
                travelService.endTravel(travel.getId());
            });

            verify(redisTrackingService, never()).clearTravelLocationCache(any());
            verify(travelRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when travel id not found from database")
        void throwExceptionWhenTravelIdNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelService.endTravel(travel.getId()));

            // se a viagem não for encontrada, nada mais deve acontecer no método
            verifyNoInteractions(
                    studentTravelRepository,
                    travelLocationHistoryRepository,
                    polylineService,
                    redisTrackingService,
                    travelReportsRepository
            );

            verifyNoMoreInteractions(travelRepository);
        }

        @Test
        @DisplayName("throw exception when travel status is pending")
        void throwExceptionWhenTravelStatusIsPending() {
            travel.setTravelStatus(TravelStatus.PENDING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelService.endTravel(travel.getId()));

            // se a viagem não for encontrada, nada mais deve acontecer no método
            verifyNoInteractions(
                    studentTravelRepository,
                    travelLocationHistoryRepository,
                    polylineService,
                    redisTrackingService,
                    travelReportsRepository
            );

            verifyNoMoreInteractions(travelRepository);

        }

        @Test
        @DisplayName("throw exception when travel status is finish")
        void throwExceptionWhenTravelStatusIsFinish() {
            travel.setTravelStatus(TravelStatus.FINISH);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelService.endTravel(travel.getId()));

            // se a viagem não for encontrada, nada mais deve acontecer no método
            verifyNoInteractions(
                    studentTravelRepository,
                    travelLocationHistoryRepository,
                    polylineService,
                    redisTrackingService,
                    travelReportsRepository
            );

            verifyNoMoreInteractions(travelRepository);

        }

        @Test
        @DisplayName("should set zero if the travel has no student")
        void shouldSetZeroIfTheTravelHasNoStudent() {
            travel.setStudentTravels(Set.of());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            String polylineRoute = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
            List<TravelLocationHistory> locationHistories = List.of(new TravelLocationHistory());

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn(String.valueOf(1500.0));
            when(travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId()))
                    .thenReturn(locationHistories);
            when(polylineService.formattedPolylineEncoded(anyList())).thenReturn(polylineRoute);

            // act
            travelService.endTravel(travel.getId());

            // assert
            assertEquals(TravelStatus.FINISH, travel.getTravelStatus());
            assertNotNull(travel.getEndHourTravel());

            int remainder = 0; // % de ocupação em 0% pq não ha estudantes
            verify(travelReportsRepository, times(1)).save(travelReportsCaptor.capture());
            assertEquals(0, travelReportsCaptor.getValue().getBusExpectedStudents());
            assertEquals(0, travelReportsCaptor.getValue().getBusActualOccupancy());
            assertEquals(remainder, travelReportsCaptor.getValue().getOccupancyPercentage());

            assertEquals(1500.0, travelReportsCaptor.getValue().getDistanceTraveled());
            assertTrue(travelReportsCaptor.getValue().getDurationInMinutes() > 0);

            verify(redisTrackingService, times(1)).clearTravelLocationCache(any());
        }
    }

    @Nested
    class joinTravel {

        @Test
        @DisplayName("should student join travel with success")
        void shouldStudentJoinTravelWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.getReferenceById(student.getId())).thenReturn(student);
            when(studentTravelRepository.save(any(StudentTravel.class))).thenReturn(studentTravel);

            travelService.joinTravel(travel.getId(), student.getId());

            ArgumentCaptor<StudentTravel> studentTravelCaptor = ArgumentCaptor.forClass(StudentTravel.class);

            verify(studentTravelRepository, times(1)).save(studentTravelCaptor.capture());
            StudentTravel storedValue = studentTravelCaptor.getValue();

            assertEquals(travel, storedValue.getTravel());
            assertEquals(student, storedValue.getStudent());
            assertTrue(storedValue.isEmbark());
            assertNotNull(storedValue.getEmbarkHour());
        }

        @ParameterizedTest
        @DisplayName("throw exception when require parameters is null")
        @MethodSource("nullParametersProvider")
        void throwExceptionWhenRequireParametersIsNull(UUID travelId, UUID studentId) {
            assertThrows(TravelException.class, () -> travelService.joinTravel(travelId, studentId));

            verify(travelRepository, never()).getReferenceById(any());
            verify(studentRepository, never()).getReferenceById(any());
            verify(studentTravelRepository, never()).save(any());
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(null, UUID.randomUUID()),
                    Arguments.of(UUID.randomUUID(), null)
            );
        }

        @ParameterizedTest
        @MethodSource("invalidTravelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus status) {
            travel.setTravelStatus(status);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);

            assertThrows(TravelException.class, () -> travelService.joinTravel(travel.getId(), student.getId()));
        }

        static Stream<Arguments> invalidTravelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        @DisplayName("throw exception when student already linked to trip")
        void throwExceptionWhenStudentAlreadyLinkedToTrip() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, student, true, Instant.now(), null, null)
            );

            travel.setStudentTravels(studentTravels);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);

            assertThrows(StudentAlreadyLinkedToTrip.class, () -> travelService.joinTravel(travel.getId(), student.getId()));

            verifyNoInteractions(
                    studentTravelRepository,
                    travelLocationHistoryRepository,
                    polylineService,
                    redisTrackingService,
                    travelReportsRepository
            );

            verifyNoMoreInteractions(travelRepository);
        }
    }
}