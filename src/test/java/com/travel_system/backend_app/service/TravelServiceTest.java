package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.enums.*;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private PushNotificationService pushNotificationService;

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
        travel = new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.PENDING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>(), new City()), Instant.now(), Instant.now(),null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345, "Feira de Santana");

        travelRequestDTO = new TravelRequestDTO(UUID.randomUUID(), -38.501234, -12.973456, -38.512345, -12.985678, "Feira de Santana");

        driver = new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>(), new City());

        student = new Student(UUID.randomUUID(), "student@gmail.com", "123456", "Maria", "Oliveira", "75988888888", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), InstitutionType.UNIVERSITY, "Computer Science");

        studentTravel = new StudentTravel(UUID.randomUUID(), new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.TRAVELLING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>(), new City()), Instant.now(), Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345, "Feira de Santana"),
                new Student(), true, Instant.now(), null, new GeoPosition(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), null), StudentTravelStatus.ACTIVE);

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

            assertThrows(InactiveDriverException.class, () -> travelService.createTravel(travelRequestDTO));

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
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now().minusSeconds(200), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE)
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
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE)
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
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now().minusSeconds(200), null, null, StudentTravelStatus.ACTIVE),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null, StudentTravelStatus.ACTIVE)
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
            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(studentTravelRepository.save(any(StudentTravel.class))).thenReturn(studentTravel);

            travelService.joinTravel(travel.getId(), student.getEmail());

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
        void throwExceptionWhenRequireParametersIsNull(UUID travelId, String studentEmail) {
            assertThrows(IllegalArgumentException.class, () -> travelService.joinTravel(travelId, studentEmail));

            verify(travelRepository, never()).getReferenceById(any());
            verify(studentRepository, never()).getReferenceById(any());
            verify(studentTravelRepository, never()).save(any());
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(null, "student@gmail.com"),
                    Arguments.of(UUID.randomUUID(), null)
            );
        }

        @ParameterizedTest
        @MethodSource("invalidTravelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus status) {
            travel.setTravelStatus(status);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);

            assertThrows(TravelException.class, () -> travelService.joinTravel(travel.getId(), student.getEmail()));
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
                    new StudentTravel(UUID.randomUUID(), travel, student, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE)
            );

            travel.setStudentTravels(studentTravels);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

            assertThrows(StudentAlreadyLinkedToTrip.class, () -> travelService.joinTravel(travel.getId(), student.getEmail()));

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

    @Nested
    class leaveTravel {

        @Test
        @DisplayName("should student leave travel with success")
        void shouldStudentLeaveTravelWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            StudentTravel studentTravel = new StudentTravel(
                    UUID.randomUUID(),
                    travel,
                    student,
                    true,
                    Instant.now(),
                    null,
                    null, StudentTravelStatus.ACTIVE
            );

            travel.setStudentTravels(Set.of(studentTravel));

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
            when(studentTravelRepository.findByTravelIdAndStudentId(travel.getId(), student.getId()))
                    .thenReturn(Optional.of(studentTravel));

            travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT);

            ArgumentCaptor<StudentTravel> studentTravelCaptor = ArgumentCaptor.forClass(StudentTravel.class);

            verify(studentTravelRepository).save(studentTravelCaptor.capture());

            StudentTravel savedStudentTravel = studentTravelCaptor.getValue();

            assertFalse(savedStudentTravel.isEmbark());
            assertNotNull(savedStudentTravel.getDisembarkHour());

            verify(travelRepository).getReferenceById(travel.getId());
            verify(studentRepository).findByEmail(student.getEmail());
            verify(studentTravelRepository).findByTravelIdAndStudentId(travel.getId(), student.getId());
        }

        @ParameterizedTest
        @DisplayName("throw exception when require params are null")
        @MethodSource("nullFieldsProvider")
        void throwExceptionWhenRequireParamsAreNull(UUID travelId, String studentEmail) {
            assertThrows(IllegalArgumentException.class, () -> travelService.leaveTravel(travelId, studentEmail, StudentTravelStatus.LEFT));

            verify(travelRepository, never()).getReferenceById(any());

            verify(studentTravelRepository, never()).findByTravelIdAndStudentId(any(), any());
            verify(studentTravelRepository, never()).save(any());
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, "student@gmail.com"),
                    Arguments.of(UUID.randomUUID(), null),
                    Arguments.of(null, null)
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when travel was not travelling")
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelWasNotTravelling(TravelStatus travelStatus) {
            travel.setTravelStatus(travelStatus);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);

            assertThrows(TravelException.class, () -> travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT));

            verify(travelRepository, times(1)).getReferenceById(any());

            verify(studentTravelRepository, never()).findByTravelIdAndStudentId(any(), any());
            verify(studentTravelRepository, never()).save(any());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        @DisplayName("throw exception when has no student travels in this trip")
        void throwExceptionWhenHasNoStudentTravelsInThisTrip() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

            assertThrows(TravelStudentAssociationNotFoundException.class, () -> travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT));

            verify(travelRepository, times(1)).getReferenceById(any());

            verify(studentTravelRepository, never()).findByTravelIdAndStudentId(any(), any());
            verify(studentTravelRepository, never()).save(any());
        }

        @Test
        @DisplayName("throw exception when isEmbark returns false")
        void throwExceptionWhenIsEmbarkReturnsFalse() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, student, false, Instant.now(), null, null, StudentTravelStatus.ACTIVE)
            );
            travel.setStudentTravels(studentTravels);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

            assertThrows(TravelStudentAssociationNotFoundException.class, () -> {
                travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT);
            });

            verify(travelRepository, times(1)).getReferenceById(any());
            verify(studentTravelRepository, never()).findByTravelIdAndStudentId(any(), any());

            verify(studentTravelRepository, never()).save(any());

        }

        @Test
        @DisplayName("throw exception when studentTravel link not found")
        void throwExceptionWhenStudentTravelLinkNotFound() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, student, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE)
            );
            travel.setStudentTravels(studentTravels);

            when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
            when(studentRepository.findByEmail(any())).thenReturn(Optional.of(student));

            assertThrows(TravelStudentAssociationNotFoundException.class, () -> {
                travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT);
            });

            verify(travelRepository, times(1)).getReferenceById(any());
            verify(studentTravelRepository, times(1)).findByTravelIdAndStudentId(any(), any());

            verify(studentTravelRepository, never()).save(any());

        }
    }

    @Nested
    class linkedStudentTravel {

        @Test
        @DisplayName("should display linked student travel with success")
        void shouldDisplayStudentTravelWithSuccess() {
            travel.setStudentTravels(Set.of(studentTravel));

            when(travelRepository.findByIdWithStudents(eq(travel.getId()))).thenReturn(Optional.of(travel));

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, student, true, Instant.now(), null, null, StudentTravelStatus.ACTIVE)
            );
            travel.setStudentTravels(studentTravels);

            Set<StudentTravelResponseDTO> result = travelService.linkedStudentTravel(travel.getId());

            assertFalse(result.isEmpty());

            verify(travelRepository, times(1)).findByIdWithStudents(any());
        }

        @Test
        @DisplayName("throw exception when travel not found")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findByIdWithStudents(travel.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> travelService.linkedStudentTravel(travel.getId()));

            verify(travelRepository, times(1)).findByIdWithStudents(any());
        }

        @Test
        @DisplayName("throw exception when has no student on this trip")
        void throwExceptionWhenHasNoStudentOnThisTrip() {
            travel.setStudentTravels(null);

            when(travelRepository.findByIdWithStudents(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(StudentNotLinkedToTripException.class, () -> travelService.linkedStudentTravel(travel.getId()));

            verify(travelRepository, times(1)).findByIdWithStudents(any());
        }
    }

    @Nested
    class isStudentLogged {

        @Test
        @DisplayName("should return true when is student logged")
        void shouldReturnTrueWhenIsStudentLogged() {
            when(studentTravelRepository.existsByIdAndTravelId(student.getId(), travel.getId()))
                    .thenReturn(true);

            boolean result = travelService.isStudentLogged(student.getId(), travel.getId());

            assertTrue(result);

            verify(studentTravelRepository, times(1)).existsByIdAndTravelId(any(), any());
        }

        @Test
        @DisplayName("should return false when student is notlogged")
        void shouldReturnFalseWhenStudentIsNotLogged() {
            when(studentTravelRepository.existsByIdAndTravelId(student.getId(), travel.getId()))
                    .thenReturn(false);

            boolean result = travelService.isStudentLogged(student.getId(), travel.getId());

            assertFalse(result);

            verify(studentTravelRepository, times(1)).existsByIdAndTravelId(any(), any());
        }
    }

    @Nested
    class isDriverLogged {

        @Test
        @DisplayName("should return true when is driver logged")
        void shouldReturnTrueWhenIsDriverLogged() {
            when(travelRepository.existsByIdAndDriverId(travel.getId(), driver.getId()))
                    .thenReturn(true);

            boolean result = travelService.isDriverLogged(driver.getId().toString(), travel.getId());

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false when driver is not logged")
        void shouldReturnFalseWhenDriverIsNotLogged() {
            when(travelRepository.existsByIdAndDriverId(travel.getId(), driver.getId()))
                    .thenReturn(false);

            boolean result = travelService.isDriverLogged(driver.getId().toString(), travel.getId());

            assertFalse(result);
        }

        @Test
        @DisplayName("")
        void shouldReturnFalseSilentlyWhenErrorOccurs() {
            String invalidUserId = "invalid-uuid";

            boolean result = travelService.isDriverLogged(invalidUserId, travel.getId());

            assertFalse(result);

            verify(travelRepository, never()).existsByIdAndDriverId(any(), any());
        }
    }

    @Nested
    class processStudentAwayState {
        UUID travelId;
        Travel travelEntity;
        Student studentEntity;
        StudentTravel studentTravelEntity;
        LiveLocationDTO liveLocationDTO;
        DistanceResponseDTO distanceResponse;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();

            // student
            studentEntity = new Student();
            studentEntity.setId(UUID.randomUUID());
            studentEntity.setEmail("student@email.com");

            // position
            GeoPosition position = new GeoPosition(
                    UUID.randomUUID(),
                    -23.55,
                    -46.63,
                    Instant.now(),
                    null
            );

            // studentTravel
            studentTravelEntity = new StudentTravel();
            studentTravelEntity.setId(UUID.randomUUID());
            studentTravelEntity.setStudent(studentEntity);
            studentTravelEntity.setPosition(position);
            studentTravelEntity.setEmbark(true);
            studentTravelEntity.setStudentTravelStatus(StudentTravelStatus.ACTIVE);

            // travel
            travelEntity = new Travel();
            travelEntity.setId(travelId);
            travelEntity.setStudentTravels(new HashSet<>(Set.of(studentTravelEntity)));
            travelEntity.setTravelStatus(TravelStatus.TRAVELLING);

            studentTravel.setTravel(travelEntity);

            liveLocationDTO = new LiveLocationDTO(-23.55, -46.63, null, null, null, null);
            distanceResponse = new DistanceResponseDTO(studentEntity.getId(), 300 + 100.0);
        }

        @Nested
        class successScenarios {
            @Test
            void shouldMarkStudentAsAwayWhenNoTimestampExists() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(pushNotificationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(null);

                travelService.processStudentAwayState(travelId, liveLocationDTO);

                ArgumentCaptor<StudentTravel> stArgCaptor = ArgumentCaptor.forClass(StudentTravel.class);
                verify(studentTravelRepository, times(1)).save(stArgCaptor.capture());
                StudentTravel savedValue = stArgCaptor.getValue();

                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, savedValue.getStudentTravelStatus());

                verify(redisTrackingService, times(1)).markStudentAsAway(eq(travelId), eq(distanceResponse));

            }

            @Test
            void shouldKeepStudentAwayWhenDisconnectTimeHasNotElapsed() {
                studentTravel.setStudentTravelStatus(StudentTravelStatus.AWAY_FROM_BUS);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(pushNotificationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));

                // timestamp recente, tempo ainda não esgotado
                long recentTimestamp = Instant.now().toEpochMilli();
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(recentTimestamp);

                travelService.processStudentAwayState(travelId, liveLocationDTO);

                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, studentTravel.getStudentTravelStatus());

                verify(redisTrackingService, never()).markStudentAsAway(any(), any());
                verify(redisTrackingService, never()).clearStudentAwayState(any(), any());
                verify(studentTravelRepository, never()).save(any());
            }

            @Test
            @DisplayName("Should set AUTO_DISCONNECTED status and remove student from travel when elapsed time exceeds auto disconnect threshold")
            void shouldAutoDisconnectStudentWhenElapsedTimeExceedsAutoDisconnectThreshold() {
                studentTravel.setStudent(studentEntity);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(travelRepository.getReferenceById(travelId)).thenReturn(travelEntity);

                when(pushNotificationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));

                when(studentRepository.findByEmail(any())).thenReturn(Optional.of(studentEntity));
                when(studentTravelRepository.findByTravelIdAndStudentId(any(), any())).thenReturn(Optional.of(studentTravel));

                long millis = TimeUnit.MINUTES.toMillis(7);
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(millis);

                travelService.processStudentAwayState(travelId, liveLocationDTO);

                ArgumentCaptor<StudentTravel> stArgCaptor = ArgumentCaptor.forClass(StudentTravel.class);

                verify(studentTravelRepository, times(1)).save(stArgCaptor.capture());
                StudentTravel storageValue = stArgCaptor.getValue();

                assertEquals(StudentTravelStatus.AUTO_DISCONNECTED, storageValue.getStudentTravelStatus());

                verify(redisTrackingService, times(1)).clearStudentAwayState(eq(travelId), eq(distanceResponse));
            }

            @Test
            @DisplayName("Should set student status to ACTIVE and clear away state when distance is within allowed range")
            void shouldSetStudentAsActiveWhenDistanceIsWithinAllowedRange() {
                studentTravel.setStudentTravelStatus(StudentTravelStatus.AWAY_FROM_BUS);
                DistanceResponseDTO distanceResponseDTO = new DistanceResponseDTO(studentEntity.getId(), 100.0);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(pushNotificationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponseDTO));

                travelService.processStudentAwayState(travelEntity.getId(), liveLocationDTO);

                verify(redisTrackingService, times(1)).clearStudentAwayState(any(), any());

                ArgumentCaptor<StudentTravel> stArgCaptor = ArgumentCaptor.forClass(StudentTravel.class);
                verify(studentTravelRepository, times(1)).save(stArgCaptor.capture());
                StudentTravel storageValue = stArgCaptor.getValue();

                assertEquals(StudentTravelStatus.ACTIVE, storageValue.getStudentTravelStatus());
            }

            @Test
            @DisplayName("Should process each student independently when some students match the filter and others do not")
            void shouldProcessEachStudentIndependentlyWhenSomeMatchFilterAndOthersDoNot() {
                Student student2 = new Student();
                student2.setId(UUID.randomUUID());
                student2.setEmail("student2@email.com");

                GeoPosition position2 = new GeoPosition(
                        UUID.randomUUID(),
                        -23.60,
                        -46.70,
                        Instant.now(),
                        null
                );

                StudentTravel studentTravel2 = new StudentTravel();
                studentTravel2.setId(UUID.randomUUID());
                studentTravel2.setStudent(student2);
                studentTravel2.setPosition(position2);
                studentTravel2.setEmbark(false);
                studentTravel2.setStudentTravelStatus(StudentTravelStatus.ACTIVE);

                travelEntity.setStudentTravels(new HashSet<>(Set.of(studentTravelEntity, studentTravel2)));

                DistanceResponseDTO distanceResponse2 = new DistanceResponseDTO(student2.getId(), 300 + 100.0);
                // fim do setUp basico

                // teste
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

                when(pushNotificationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse, distanceResponse2));

                when(redisTrackingService.getStudentAwayTimestamp(eq(travelId), any())).thenReturn(null);

                travelService.processStudentAwayState(travelId, liveLocationDTO);

                assertEquals(StudentTravelStatus.AWAY_FROM_BUS, studentTravelEntity.getStudentTravelStatus());
                verify(redisTrackingService, times(1)).markStudentAsAway(eq(travelId), eq(distanceResponse));

                assertEquals(StudentTravelStatus.ACTIVE, studentTravel2.getStudentTravelStatus());
                verify(redisTrackingService, never()).markStudentAsAway(eq(travelId), eq(distanceResponse2));
            }
        }

        @Nested
        class failureScenarios {

            @Test
            void throwExceptionWhenTravelNotFound() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> travelService.processStudentAwayState(travelId, liveLocationDTO));

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            void throwExceptionWhenTravelIsNotTravelling() {
                travelEntity.setTravelStatus(TravelStatus.PENDING);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

                assertThrows(TravelException.class, () -> travelService.processStudentAwayState(travelId, liveLocationDTO));

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            @DisplayName("should log warning and ignore student when no matching StudentTravel is found")
            void shouldIgnoreStudentWhenNoMatchingStudentTravelIsFound() {
                travelEntity.setStudentTravels(null);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

                travelService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }
        }

    }

}