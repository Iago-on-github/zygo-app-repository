package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelCacheDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.*;
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
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {
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
    @Mock
    private TravelCacheService travelCacheService;
    @Mock
    private TravelNotificationService travelNotificationService;
    @Mock
    private TravelStudentStateCacheService travelStudentStateCacheService;

    @InjectMocks
    private TravelService travelService;

    private final ArgumentCaptor<TravelReports> travelReportsCaptor = ArgumentCaptor.forClass(TravelReports.class);

    TravelRequestDTO travelRequestDTO;
    Driver driver;
    Travel travel;
    Customer customer;
    Student student;
    StudentTravel studentTravel;

    @BeforeEach
    void setUp() {
        customer = new Customer(UUID.randomUUID(), "Universidade Exemplo", "universidade-exemplo", "12.345.678/0001-90", true, new City(), ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));

        driver = new Driver(UUID.randomUUID(), "joao.silva@exemplo.com", "Senha@123", "João", "Silva", "+55 11 98888-7777", "https://cdn.exemplo.com/drivers/joao-silva.png", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 16, 12, 30), LocalDateTime.of(2026, 7, 16, 12, 30), customer, "Transporte Escolar", 24);

        travelRequestDTO = new TravelRequestDTO(UUID.randomUUID(), TravelPeriod.MORNING, -38.501234, -12.973456, -38.512345, -12.985678, "Feira de Santana");

        student = new Student(UUID.randomUUID(), "ana.souza@exemplo.com", "Senha@123", "Ana", "Souza", "+55 11 99999-1234", "https://cdn.exemplo.com/students/ana-souza.png", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 16, 12, 0), LocalDateTime.of(2026, 7, 16, 12, 0), customer, InstitutionType.UNIVERSITY, "Engenharia de Software");

        studentTravel = new StudentTravel(UUID.randomUUID(), travel, student, true, Instant.parse("2026-07-16T10:20:00Z"), null, null, StudentTravelStatus.ACTIVE);

        travel = new Travel(UUID.randomUUID(), TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:10:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
    }

    @Nested
    class createTravel {
        TravelPreviewDTO travelPreviewDTO;

        @BeforeEach
        void setUp() {
            travelPreviewDTO = new TravelPreviewDTO(30.0, 60.0, "Feira de Santana", "16:14");
        }

        @Test
        @DisplayName("should create travel with success")
        void shouldCreateTravelWithSuccess() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));
            when(travelRepository.save(any(Travel.class))).thenReturn(new Travel());
            when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(travelPreviewDTO);

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
        void throwExceptionWhenDriverHasActiveTravel() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));

            when(travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING)))
                    .thenReturn(true);

            assertThrows(TravelException.class, () -> travelService.createTravel(travelRequestDTO));

            verify(driverRepository, times(1)).findById(any());
            verify(travelRepository, times(1)).existsByDriverIdAndTravelStatusIn(any(), anyList());

            verify(travelRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exception quando o período da viagem não for encontrado")
        void throwTravelExceptionWhenTravelPeriodIsNull() {
            TravelRequestDTO newTravelReqDTO = new TravelRequestDTO(UUID.randomUUID(), null, -38.501234, -12.973456, -38.512345, -12.985678, "Feira de Santana");

            when(driverRepository.findById(newTravelReqDTO.driverId())).thenReturn(Optional.of(driver));

            assertThrows(TravelException.class, () -> travelService.createTravel(newTravelReqDTO));

            verify(mapboxAPIService, never()).getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            verify(travelRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar exception quando a MAPBOX api retornar null")
        void ThrowExceptionWhenMapboxPreviewDataReturnsNull() {
            when(driverRepository.findById(travelRequestDTO.driverId())).thenReturn(Optional.of(driver));
            when(mapboxAPIService.getTripPreview(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenThrow(new RecalculateEtaException(""));

            assertThrows(RecalculateEtaException.class, () -> travelService.createTravel(travelRequestDTO));

            verify(travelRepository, never()).save(any());
        }


    }

    @Nested
    class startTravel {
        @BeforeEach
        void setUp() {
            travel.setTravelStatus(TravelStatus.PENDING);
        }

        @Test
        @DisplayName("should start travel with success")
        void shouldStartTravelWithSuccess() {
            RouteDetailsDTO routeDetailsDTO = new RouteDetailsDTO(3600.0, 15.5, "encoded_polyline_example");

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(mapboxAPIService.calculateRoute(travel.getOriginLongitude(), travel.getOriginLatitude(), travel.getFinalLongitude(), travel.getFinalLatitude(), anyList()))
                    .thenReturn(routeDetailsDTO);
            when(travelRepository.save(any(Travel.class))).thenReturn(travel);
            doNothing().when(travelCacheService).invalidateTravelStaticCache(travel.getId());

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
            verify(travelRepository, times(1)).save(any());
            verify(travelNotificationService, times(1)).sendTravelStartedNotification(travel);
            verify(travelCacheService, times(1)).invalidateTravelStaticCache(travel.getId());
        }

        @Test
        @DisplayName("throw exception when travel not found from database")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
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

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
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

            verify(mapboxAPIService, never()).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

        @Test
        @DisplayName("throw exception when mapbox api returns null from route details")
        void throwExceptionWhenMapBoxAPIReturnsNullFromRouteDetails() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            when(mapboxAPIService.calculateRoute(travel.getOriginLongitude(), travel.getOriginLatitude(), travel.getFinalLongitude(), travel.getFinalLatitude(), anyList()))
                    .thenReturn(null);

            assertThrows(RecalculateEtaException.class, () -> travelService.startTravel(travel.getId()));

            verify(travelRepository, times(1)).findById(any());
            verify(mapboxAPIService, times(1)).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());

            verify(travelRepository, never()).save(any());
            verify(redisTrackingService, never()).addActiveTravel(any());
        }

    }

    @Nested
    class endTravel {

        @DisplayName("should generate metrics to Travel Reports with success")
        @Test
        void shouldGenerateFullTravelReportWithSuccess() {
            String polylineRoute = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            travel.setStudentTravels(Set.of(studentTravel));

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

            int remainder = (1 * 100); // dividido pelo total de estudantes (nesse caso 1 mas foi simplificado)
            verify(travelReportsRepository, times(1)).save(travelReportsCaptor.capture());
            assertEquals(1, travelReportsCaptor.getValue().getBusExpectedStudents());
            assertEquals(1, travelReportsCaptor.getValue().getBusActualOccupancy());
            assertEquals(remainder, travelReportsCaptor.getValue().getOccupancyPercentage());

            assertEquals(1500.0, travelReportsCaptor.getValue().getDistanceTraveled());
            assertTrue(travelReportsCaptor.getValue().getDurationInMinutes() > 0);

            verify(redisTrackingService, times(1)).clearTravelLocationCache(any());
        }

        @Test
        @DisplayName("should validate the exactly percentual of occupancy")
        void shouldGeneratePartialOccupancyReport() {
            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            Set<StudentTravel> studentTravels = Set.of(studentTravel);

            travel.setStudentTravels(studentTravels);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn("100.0");

            travelService.endTravel(travel.getId());

            verify(travelReportsRepository, times(1)).save(travelReportsCaptor.capture());

            assertEquals(1, travelReportsCaptor.getValue().getBusExpectedStudents());
            assertEquals(1, travelReportsCaptor.getValue().getBusActualOccupancy());
            assertEquals(100, travelReportsCaptor.getValue().getOccupancyPercentage());

            assertTrue(travel.getStudentTravels().stream().noneMatch(StudentTravel::isEmbark));
        }

        @Test
        @DisplayName("should rollback if an error occurs and keep travel status unchanged")
        void shouldRollbackWhenTravelReportsSaveFails() {
            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now());

            Set<StudentTravel> studentTravels = Set.of(studentTravel);

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

            doNothing().when(travelStudentStateCacheService).evictStudentTravelCachedData(travel.getId(), student.getEmail());

            travelService.joinTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE);

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
            assertThrows(IllegalArgumentException.class, () -> travelService.joinTravel(travelId, studentEmail, StudentTravelStatus.ACTIVE));

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

            assertThrows(TravelException.class, () -> travelService.joinTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));
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
            Set<StudentTravel> studentTravels = Set.of(studentTravel);
            travel.setStudentTravels(studentTravels);

            when(studentTravelRepository.existsByTravelIdAndStudentEmailAndEmbarkTrue(travel.getId(), student.getEmail())).thenThrow(new StudentAlreadyLinkedToTrip(""));

            assertThrows(StudentAlreadyLinkedToTrip.class, () -> travelService.joinTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));

            verifyNoInteractions(
                    travelLocationHistoryRepository,
                    polylineService,
                    redisTrackingService,
                    travelReportsRepository
            );

            verifyNoMoreInteractions(travelRepository);
        }

       @Test
       @DisplayName("Deve lançar exception quando o estudante não for encontrado")
       void throwExceptionWhenStudentNotFound() {
           when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.empty());

           when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
           when(studentTravelRepository.existsByTravelIdAndStudentEmailAndEmbarkTrue(travel.getId(), student.getEmail())).thenReturn(false);

           assertThrows(EntityNotFoundException.class, () -> travelService.joinTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));

           verifyNoMoreInteractions(studentTravelRepository, travelStudentStateCacheService, travelRepository, studentRepository);
       }

       @Test
       @DisplayName("Deve lançar exception quando o estudante for de um Customer difernete da viagem")
       void throwExceptionWhenStudentAndTravelBelongToDifferentCustomers() {
            student.setCustomer(new Customer()); // different customer

           when(travelRepository.getReferenceById(travel.getId())).thenReturn(travel);
           when(studentRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));

           assertThrows(TravelException.class, () -> travelService.joinTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));

           verify(studentTravelRepository, never()).save(any());
           verify(travelStudentStateCacheService, never()).evictStudentTravelCachedData(any(), any());
       }


   }

   @Nested
   class driverChanged {

       @Test
       @DisplayName("Deve mudar o motorista da viagem com sucesso")
       void shouldDriverChangedWithSuccess() {
           Driver actualDriver = new Driver(UUID.randomUUID(), "joao.silva@exemplo.com", "Senha@123", "João", "Silva", "+55 11 98888-7777", "https://cdn.exemplo.com/drivers/joao-silva.png", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 16, 12, 30), LocalDateTime.of(2026, 7, 16, 12, 30), customer, "Transporte Escolar", 24);

           travel.setDriver(actualDriver);

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
           when(travelRepository.save(travel)).thenReturn(travel);

           travelService.driverChanged(travel.getId(), driver.getId());

           ArgumentCaptor<Travel> travelArgCaptor = ArgumentCaptor.forClass(Travel.class);

           verify(travelRepository, times(1)).save(travelArgCaptor.capture());

           Travel storedValue = travelArgCaptor.getValue();
           assertEquals(storedValue.getDriver(), travel.getDriver());

           verify(travelNotificationService, times(1)).sendDriverChangedNotification(any(), any());
       }

       @Test
       @DisplayName("Deve lançar exception quando a viagem não for encontrada")
       void throwExceptionWhenTravelNotFound() {
           when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

           assertThrows(TripNotFound.class, () -> travelService.driverChanged(travel.getId(), driver.getId()));

           verifyNoInteractions(driverRepository, travelNotificationService);

           verifyNoMoreInteractions(travelRepository);
       }

       @ParameterizedTest
       @DisplayName("Deve lançar exception quando a viagem estiver inabilitada")
       @MethodSource("travelStatusProvider")
       void throwTravelExceptionWhenChangingDriverOfFinishedTravel(TravelStatus travelStatus) {
           travel.setTravelStatus(travelStatus);

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

           assertThrows(TravelException.class, () -> travelService.driverChanged(travel.getId(), driver.getId()));

           verifyNoInteractions(driverRepository, travelNotificationService);

           verifyNoMoreInteractions(travelRepository);
       }

       public static Stream<Arguments> travelStatusProvider() {
           return Stream.of(
                   Arguments.of(TravelStatus.FINISH),
                   Arguments.of(TravelStatus.CANCELED)
           );
       }

       @Test
       @DisplayName("Deve lançar exception quando o novo motorista não existir")
       void throwEntityNotFoundExceptionWhenNewDriverDoesNotExist() {
           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(driverRepository.findById(driver.getId())).thenReturn(Optional.empty());

           assertThrows(EntityNotFoundException.class, () -> travelService.driverChanged(travel.getId(), driver.getId()));

           verifyNoMoreInteractions(travelRepository, driverRepository);

           verifyNoInteractions(travelNotificationService);
       }

       @Test
       @DisplayName("Deve lançar exception quando o novo motorista já estiver com uma viagem ativa")
       void throwTravelExceptionWhenNewDriverAlreadyHasActiveTravel() {
           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
           when(travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING))).thenReturn(true);

           assertThrows(TravelException.class, () -> travelService.driverChanged(travel.getId(), driver.getId()));

           verifyNoMoreInteractions(travelRepository, driverRepository);
           verifyNoInteractions(travelNotificationService);
       }

       @Test
       @DisplayName("Deve lançar exception quando o novo motorista faz parte de outros customers diferentes")
       void throwTravelExceptionWhenDriversBelongToDifferentCustomers() {
           Customer customerA = new Customer();
           customerA.setId(UUID.randomUUID());

           Driver currentDriver = new Driver();
           currentDriver.setId(UUID.randomUUID());
           currentDriver.setCustomer(customerA);

           travel.setDriver(currentDriver);

           Customer customerB = new Customer();
           customerB.setId(UUID.randomUUID());

           Driver newDriver = new Driver();
           newDriver.setId(UUID.randomUUID());
           newDriver.setStatus(GeneralStatus.ACTIVE);
           newDriver.setCustomer(customerB);

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(driverRepository.findById(newDriver.getId())).thenReturn(Optional.of(newDriver));

           when(travelRepository.existsByDriverIdAndTravelStatusIn(eq(newDriver.getId()), any())).thenReturn(false);

           assertThrows(TravelException.class, () -> travelService.driverChanged(travel.getId(), newDriver.getId()));

           verifyNoInteractions(travelNotificationService);
       }

   }

   @Nested
   class cancelTravel {

       @Test
       @DisplayName("Deve cancelar a viagem com sucesso")
       void shouldCancelPendingTravelWithoutStudents() {
           travel.setTravelStatus(TravelStatus.PENDING);

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(travelRepository.save(travel)).thenReturn(travel);

           travelService.cancelTravel(travel.getId());

           ArgumentCaptor<Travel> travelArgCaptor = ArgumentCaptor.forClass(Travel.class);

           verify(travelRepository, times(1)).save(travelArgCaptor.capture());

           Travel storedValue = travelArgCaptor.getValue();
           assertEquals(TravelStatus.CANCELED, storedValue.getTravelStatus());
           assertEquals(0, storedValue.getStudentTravels().size());

           assertNotNull(storedValue.getEndHourTravel());

           verify(travelNotificationService, times(1)).sendTravelCanceledNotification(travel);

       }

       @Test
       @DisplayName("Deve lançar exception quando a viagem não for encontrada")
       void throwTripNotFoundWhenCancelingNonExistingTravel() {
           when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

           assertThrows(TripNotFound.class, () -> travelService.cancelTravel(travel.getId()));

           verifyNoMoreInteractions(travelRepository);
           verifyNoInteractions(studentTravelRepository, travelNotificationService);
       }

       @ParameterizedTest
       @DisplayName("Deve lançar exception quando a viagem já estiver em andamento ou finalizada")
       @MethodSource("travelStatusProvider")
       void throwTravelExceptionWhenCancelingTravellingTravel(TravelStatus travelStatus) {
           travel.setTravelStatus(travelStatus);

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

           assertThrows(TravelException.class, () -> travelService.cancelTravel(travel.getId()));

           verifyNoMoreInteractions(travelRepository);
           verifyNoInteractions(studentTravelRepository, travelNotificationService);
       }

       public static Stream<Arguments> travelStatusProvider() {
           return Stream.of(
                   Arguments.of(TravelStatus.FINISH),
                   Arguments.of(TravelStatus.CANCELED),
                   Arguments.of(TravelStatus.TRAVELLING)
           );
       }

       @Test
       @DisplayName("Deve desconectar os estudantes da viagem corretamente")
       void shouldDisconnectEmbarkedStudentWhenCancelingTravel() {
           travel.setTravelStatus(TravelStatus.PENDING);
           travel.setStudentTravels(Set.of(studentTravel));

           when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
           when(travelRepository.save(travel)).thenReturn(travel);
           when(studentTravelRepository.save(studentTravel)).thenReturn(studentTravel);

           travelService.cancelTravel(travel.getId());

           ArgumentCaptor<StudentTravel> stArgCaptor = ArgumentCaptor.forClass(StudentTravel.class);

           verify(studentTravelRepository, times(1)).save(stArgCaptor.capture());

           StudentTravel storedValue = stArgCaptor.getValue();
           assertFalse(storedValue.isEmbark());
           assertNotNull(storedValue.getDisembarkHour());

           verify(travelRepository, times(1)).save(any());
           verify(travelNotificationService, times(1)).sendTravelCanceledNotification(any());
       }
   }

   @Nested
   class leaveTravel {
       TravelCacheDTO travelCacheDTO;
       StudentTravelCacheDTO studentTravelCacheDTO;

       @BeforeEach
       void setUp() {
           travelCacheDTO = new TravelCacheDTO(UUID.randomUUID(), UUID.randomUUID(), customer.getId(),TravelStatus.TRAVELLING, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);
           studentTravelCacheDTO = new StudentTravelCacheDTO(studentTravel.getId(), student.getEmail(), student.getId(), StudentTravelStatus.ACTIVE, true);
       }

       @Test
        @DisplayName("should student leave travel with success")
        void shouldStudentLeaveTravelWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            travel.setStudentTravels(Set.of(studentTravel));

            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
            when(travelStudentStateCacheService.getOrLoadStudentTravelCache(travel.getId(), student.getEmail())).thenReturn(studentTravelCacheDTO);

            travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT);

            verify(studentTravelRepository, times(1)).disconnectedStudentFromTrip(anyList(), any(), any(), anyBoolean());
            verify(travelStudentStateCacheService, times(1)).evictStudentTravelCachedData(any(), anyString());
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
            TravelCacheDTO newTravelCacheDTO = new TravelCacheDTO(UUID.randomUUID(), UUID.randomUUID(), customer.getId(), travelStatus, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);

            when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(newTravelCacheDTO);
            when(travelStudentStateCacheService.getOrLoadStudentTravelCache(travel.getId(), student.getEmail())).thenReturn(studentTravelCacheDTO);

            assertThrows(TravelException.class, () -> travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.LEFT));

            verify(studentTravelRepository, never()).findByTravelIdAndStudentId(any(), any());
            verify(studentTravelRepository, never()).save(any());
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.CANCELED),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

       @Test
       @DisplayName("Deve lançar exception quando o id do estudante não estiver presente no cache")
       void throwTravelStudentAssociationNotFoundWhenStudentIdIsNull() {
           StudentTravelCacheDTO newStudentCacheDTO = new StudentTravelCacheDTO(studentTravel.getId(), student.getEmail(), null, StudentTravelStatus.ACTIVE, true);

           when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
           when(travelStudentStateCacheService.getOrLoadStudentTravelCache(travel.getId(), student.getEmail())).thenReturn(newStudentCacheDTO);

           assertThrows(TravelStudentAssociationNotFoundException.class, () -> travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));

           verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(any());
           verify(travelStudentStateCacheService, times(1)).getOrLoadStudentTravelCache(any(), anyString());

           verifyNoMoreInteractions(travelStudentStateCacheService);
           verifyNoInteractions(studentTravelRepository);
       }

       @Test
       @DisplayName("Deve lançar exception quando o estudante não estiver embarcado no cache")
       void throwTravelStudentAssociationNotFoundWhenStudentIsNotEmbarked() {
           StudentTravelCacheDTO newStudentCacheDTO = new StudentTravelCacheDTO(studentTravel.getId(), student.getEmail(), student.getId(), StudentTravelStatus.ACTIVE, false);

           when(travelCacheService.getOrLoadTravelStaticCache(travel.getId())).thenReturn(travelCacheDTO);
           when(travelStudentStateCacheService.getOrLoadStudentTravelCache(travel.getId(), student.getEmail())).thenReturn(newStudentCacheDTO);

           assertThrows(TravelStudentAssociationNotFoundException.class, () -> travelService.leaveTravel(travel.getId(), student.getEmail(), StudentTravelStatus.ACTIVE));

           verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(any());
           verify(travelStudentStateCacheService, times(1)).getOrLoadStudentTravelCache(any(), anyString());

           verifyNoMoreInteractions(travelStudentStateCacheService);
           verifyNoInteractions(studentTravelRepository);
       }
   }

   @Nested
   class linkedStudentTravel {
       StudentTrackingPositionDTO studentTrackingPositionDTO;

       @BeforeEach
       void setUp() {
           studentTrackingPositionDTO = new StudentTrackingPositionDTO(student.getId(), -32.432, -11.231);
       }

       @Test
       @DisplayName("Deve retornar os estudantes vinculados à viagem com sucesso")
       void shouldReturnLinkedStudentTrackingPositionsWhenStudentsExist() {
            when(travelRepository.findTrackingPositionsByTravelId(travel.getId())).thenReturn(Set.of(studentTrackingPositionDTO));

           Set<StudentTrackingPositionDTO> result = travelService.linkedStudentTravel(travel.getId());

           assertNotNull(result);
       }

       @Test
       @DisplayName("Deve lançar exception quando não há estudantes vinculados à viagem")
       void throwStudentNotLinkedToTripExceptionWhenTravelHasNoLinkedStudents() {
           when(travelRepository.findTrackingPositionsByTravelId(travel.getId())).thenReturn(Set.of());

           assertThrows(StudentNotLinkedToTripException.class, () -> travelService.linkedStudentTravel(travel.getId()));
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
   class getTravelPreview {
        Instant startHourTravel;
        Double duration;

        @BeforeEach
        void setUp() {
            startHourTravel = Instant.parse("2026-07-16T10:00:00Z");
            duration = 3600.0;
        }

        @Test
        @DisplayName("Deve retornar os dados de preview corretamente incluindo o arrivalTime")
        void shouldReturnTravelPreviewWithArrivalTimeWhenStartTimeAndDurationExist() {
            travel.setStartHourTravel(startHourTravel);
            travel.setDuration(duration);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            TravelPreviewDTO result = travelService.getTravelPreview(travel.getId());

            assertNotNull(result);
            assertNotNull(result.distance());
            assertNotNull(result.duration());
            assertNotNull(result.destinationCity());
            assertEquals("2026-07-16T11:00:00Z", result.arrivalTime());
        }

        @Test
        @DisplayName("Deve lançar exception quando a viagem não for encontrada")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> travelService.getTravelPreview(travel.getId()));
        }

        @Test
        @DisplayName("Deve setar o arrivelTime como null quando não houver startTime")
        void shouldReturnPreviewWithoutArrivalTimeWhenStartTimeIsNull() {
            travel.setDuration(duration);
            travel.setStartHourTravel(null);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            TravelPreviewDTO result = travelService.getTravelPreview(travel.getId());

            assertNotNull(result);
            assertNotNull(result.distance());
            assertNotNull(result.duration());
            assertNotNull(result.destinationCity());

            assertNull(result.arrivalTime());
        }
    }
}