package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelTrackingServiceTest {

    @InjectMocks
    private TravelTrackingService travelTrackingService;

    @Mock
    private TravelRepository travelRepository;
    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private MapboxAPIService mapboxAPIService;
    @Mock
    private RouteCalculationService routeCalculationService;
    @Mock
    private StudentTravelRepository studentTravelRepository;
    @Mock
    private GpsDataIngestorService gpsDataIngestorService;
    @Mock
    private TravelLocationHistoryRepository travelLocationHistoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    VehicleLocationRequestDTO vehicleLocationRequestDTO;
    Travel travel;
    LiveLocationDTO liveLocationDTO;
    NewLocationReceivedEvents newLocationReceivedEvents;
    RouteDeviationDTO routeDeviationDTO;
    RouteDetailsDTO routeDetailsDTO;
    PreviousStateDTO previousStateDTO;
    StudentTravel studentTravel;

    private static final long FIXED_TIMESTAMP = 1_700_000_000_000L;

    @BeforeEach
    void setUp() {
        vehicleLocationRequestDTO = new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, -38.501234, 60.0, 180.0);
        travel = new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.PENDING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>()), Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345);
        liveLocationDTO = new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000);
        newLocationReceivedEvents = new NewLocationReceivedEvents(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), TravelStatus.TRAVELLING, 60.0, 180.0);
        routeDeviationDTO = new RouteDeviationDTO(25.0, true, -12.972000, -38.500000);
        routeDetailsDTO = new RouteDetailsDTO(2100.0, 35.0, "encoded_polyline_example");
        previousStateDTO = new PreviousStateDTO(1200.0, 18.5, System.currentTimeMillis());
        studentTravel = new StudentTravel(UUID.randomUUID(), travel, new Student(), false, null, null, new GeoPosition());
    }

    @Nested
    class markDriverCheckpoint {

        @Test
        @DisplayName("should mark driver checkpoint with success")
        void shouldMarkDriverCheckpointWithSuccess() {
            UUID cityId = UUID.randomUUID();

            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getLiveLocation(travel.getId().toString())).thenReturn(liveLocationDTO);

            travelTrackingService.markDriverCheckpoint(cityId, travel.getId(), vehicleLocationRequestDTO);

            ArgumentCaptor<NewLocationReceivedEvents> captor = ArgumentCaptor.forClass(NewLocationReceivedEvents.class);

            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            NewLocationReceivedEvents event = captor.getValue();

            assertEquals(travel.getId(), event.travelId());
            assertEquals(vehicleLocationRequestDTO.latitude(), event.latitude());
            assertEquals(vehicleLocationRequestDTO.longitude(), event.longitude());
            assertEquals(travel.getTravelStatus(), event.status());
            assertEquals(vehicleLocationRequestDTO.speed(), event.speed());
            assertEquals(vehicleLocationRequestDTO.heading(), event.heading());
            assertNotNull(event.timestamp());

            verify(travelRepository, times(1)).findById(any());
            verify(redisTrackingService, times(1)).getLiveLocation(anyString());

            verify(redisTrackingService).storeLiveLocation(
                    eq(travel.getId().toString()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(liveLocationDTO.distance()),
                    eq(liveLocationDTO.geometry())
            );

            verify(gpsDataIngestorService, times(1)).sendVehicleGps(anyString(), anyString(), any());
        }

        @ParameterizedTest
        @DisplayName("throw exception when find mandatory fields empty")
        @MethodSource("emptyMandatoryFieldsProvider")
        void throwExceptionWhenFindMandatoryFieldsEmpty(UUID cityId, UUID travelId) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));

            verifyNoInteractions(
                    travelRepository,
                    eventPublisher,
                    redisTrackingService,
                    gpsDataIngestorService
            );
        }

        public static Stream<Arguments> emptyMandatoryFieldsProvider() {
            return Stream.of(
                    Arguments.of(null, UUID.randomUUID()),
                    Arguments.of(UUID.randomUUID(), null),
                    Arguments.of(null, null)
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when vehicleData or coords data are null")
        @MethodSource("vehicleOrCoordsDataProvider")
        void throwExceptionWhenVehicleDataOrCoordinatesDataAreNull(VehicleLocationRequestDTO vehicleLocationRequestDTO) {
            assertThrows(NoSuchCoordinates.class, () -> travelTrackingService.markDriverCheckpoint(UUID.randomUUID(), travel.getId(), vehicleLocationRequestDTO));

            verifyNoInteractions(
                    travelRepository,
                    eventPublisher,
                    redisTrackingService,
                    gpsDataIngestorService
            );
        }

        public static Stream<Arguments> vehicleOrCoordsDataProvider() {
            return Stream.of(
                    Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.501234, 60.0, 180.0)),
                    Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, null, 60.0, 180.0)),
                    Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, null, 60.0, 180.0)),
                    Arguments.of((VehicleLocationRequestDTO) null)
            );
        }

        @Test
        @DisplayName("throw exception when travel not found from database")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelTrackingService.markDriverCheckpoint(UUID.randomUUID(), travel.getId(), vehicleLocationRequestDTO));

            verify(travelRepository).findById(any());
            verifyNoMoreInteractions(travelRepository);

            verifyNoInteractions(
                    eventPublisher,
                    redisTrackingService,
                    gpsDataIngestorService
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when travelStatus is not Travelling")
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelStatusIsNotTravelling(TravelStatus travelStatus) {
            travel.setTravelStatus(travelStatus);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelTrackingService.markDriverCheckpoint(UUID.randomUUID(), travel.getId(), vehicleLocationRequestDTO));

            verify(travelRepository).findById(any());
            verifyNoMoreInteractions(travelRepository);

            verifyNoInteractions(
                    eventPublisher,
                    redisTrackingService,
                    gpsDataIngestorService
            );
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @Test
        @DisplayName("should pass 'null' from storeLiveLocation when first ping")
        void shouldStoreNullDistanceAndGeometryOnFirstPing() {
            UUID cityId = UUID.randomUUID();

            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getLiveLocation(travel.getId().toString())).thenReturn(null);

            doNothing().when(redisTrackingService)
                    .storeLiveLocation(
                            eq(travel.getId().toString()),
                            eq(vehicleLocationRequestDTO.latitude().toString()),
                            eq(vehicleLocationRequestDTO.longitude().toString()),
                            eq(null),
                            eq(null));

            travelTrackingService.markDriverCheckpoint(cityId, travel.getId(), vehicleLocationRequestDTO);

            verify(redisTrackingService, times(1)).getLiveLocation(anyString());
            verify(redisTrackingService).storeLiveLocation(
                    eq(travel.getId().toString()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(null),
                    eq(null)
            );

        }
    }

    @Nested
    class processNewLocation {

        @Test
        @DisplayName("should process new location when isRouteOff returns TRUE with success")
        void shouldProcessNewLocationWhenIsRouteOffReturnsTrueWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));
            when(routeCalculationService.isRouteDeviation(eq(vehicleLocationRequestDTO.latitude()), eq(vehicleLocationRequestDTO.longitude()), eq(travel.getPolylineRoute())))
                    .thenReturn(routeDeviationDTO);
            when(mapboxAPIService.recalculateETA(vehicleLocationRequestDTO.longitude(), vehicleLocationRequestDTO.latitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);

            doNothing().when(redisTrackingService).storeLiveLocation(
                    eq(travel.getId().toString()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(routeDetailsDTO.duration()),
                    eq(routeDetailsDTO.geometry())
            );

            doNothing().when(redisTrackingService).storeTravelMetadata(
                    eq(travel.getId().toString()),
                    eq(routeDetailsDTO.duration()),
                    eq(routeDetailsDTO.distance()),
                    eq(travel.getTravelStatus().toString())
            );

            travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

            verify(travelRepository, times(1)).findById(any());
            verify(routeCalculationService, times(1)).isRouteDeviation(anyDouble(), anyDouble(), anyString());
            verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

            verify(redisTrackingService, times(1)).storeLiveLocation(
                    eq(travel.getId().toString()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(routeDetailsDTO.duration()),
                    eq(routeDetailsDTO.geometry())
            );

            verify(redisTrackingService, times(1)).storeTravelMetadata(
                    eq(travel.getId().toString()),
                    eq(routeDetailsDTO.duration()),
                    eq(routeDetailsDTO.distance()),
                    eq(travel.getTravelStatus().toString())
            );
        }

        @Test
        @DisplayName("should process new location when isRouteOff returns FALSE with success")
        void shouldProcessNewLocationWhenIsRouteOffReturnsFalseWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            previousStateDTO = new PreviousStateDTO(10.0, 5000.0, FIXED_TIMESTAMP - 1000L);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));
            when(routeCalculationService.isRouteDeviation(eq(vehicleLocationRequestDTO.latitude()), eq(vehicleLocationRequestDTO.longitude()), eq(travel.getPolylineRoute())))
                    .thenReturn(new RouteDeviationDTO(25.0, false, -12.972000, -38.500000));
            when(redisTrackingService.getPreviousEta(String.valueOf(travel.getId()))).thenReturn(previousStateDTO);

            when(clock.millis()).thenReturn(FIXED_TIMESTAMP);

            travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

            verify(travelRepository, times(1)).findById(any());
            verify(routeCalculationService, times(1)).isRouteDeviation(anyDouble(), anyDouble(), anyString());

            verifyNoInteractions(mapboxAPIService);

            double expectedEta = 10.0 - 1.0;

            verify(redisTrackingService, times(1)).storeLiveLocation(
                    eq(travel.getId().toString()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(expectedEta),
                    eq(travel.getPolylineRoute())
            );

            verify(redisTrackingService, times(1)).storeTravelMetadata(
                    eq(travel.getId().toString()),
                    eq(expectedEta),
                    eq(travel.getDistance()),
                    eq(travel.getTravelStatus().toString())
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when require parameter data is null")
        @MethodSource("nullParametersProvider")
        void throwExceptionWhenRequireParameterDataIsNull(VehicleLocationRequestDTO vehicleLocationRequestDTO) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

            verifyNoInteractions(
                    travelRepository,
                    routeCalculationService,
                    mapboxAPIService,
                    redisTrackingService);
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(new VehicleLocationRequestDTO(null, -12.973456, -38.501234, 60.0, 180.0)),
                    Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.501234, 60.0, 180.0)),
                    Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, null, 60.0, 180.0)),
                    Arguments.of((VehicleLocationRequestDTO) null)
            );
        }

        @Test
        @DisplayName("throw exception when trip not found")
        void throwExceptionWhenTripNotFound() {
            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

            verifyNoInteractions(
                    routeCalculationService,
                    mapboxAPIService,
                    redisTrackingService);

            verifyNoMoreInteractions(travelRepository);
        }

        @ParameterizedTest
        @DisplayName("throw exception when trip is not travelling")
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTripIsNotTravelling(TravelStatus travelStatus) {
            travel.setTravelStatus(travelStatus);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

            verifyNoInteractions(
                routeCalculationService,
                mapboxAPIService,
                redisTrackingService);

            verifyNoMoreInteractions(travelRepository);
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when 'newEtaRecalculateByApi' is null from mapBoxAPI's call")
        @MethodSource("nullRecalcDistanceProvider")
        void throwExceptionWhenNewEtaRecalculateByApiIsNull(RouteDetailsDTO routeDetailsDTO) {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));
            when(routeCalculationService.isRouteDeviation(eq(vehicleLocationRequestDTO.latitude()), eq(vehicleLocationRequestDTO.longitude()), eq(travel.getPolylineRoute())))
                    .thenReturn(routeDeviationDTO);

            when(mapboxAPIService.recalculateETA(vehicleLocationRequestDTO.longitude(), vehicleLocationRequestDTO.latitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);

            assertThrows(RecalculateEtaException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

            verifyNoInteractions(redisTrackingService);

            verifyNoMoreInteractions(travelRepository, routeCalculationService, mapboxAPIService);
        }

        public static Stream<Arguments> nullRecalcDistanceProvider() {
            return Stream.of(
                    Arguments.of(new RouteDetailsDTO(null,35.0, "encoded_polyline_example")),
                    Arguments.of(new RouteDetailsDTO(2100.0, null, "encoded_polyline_example")),
                    Arguments.of((RouteDetailsDTO) null)
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when previous eta is null")
        @MethodSource("nullPreviousEtaProvider")
        void throwExceptionWhenPreviousEtaIsNull(PreviousStateDTO previousStateDTO) {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));
            when(routeCalculationService.isRouteDeviation(eq(vehicleLocationRequestDTO.latitude()), eq(vehicleLocationRequestDTO.longitude()), eq(travel.getPolylineRoute())))
                    .thenReturn(new RouteDeviationDTO(25.0, false, -12.972000, -38.500000));
            when(redisTrackingService.getPreviousEta(String.valueOf(travel.getId()))).thenReturn(previousStateDTO);

            assertThrows(EtaDataStatesInvalidException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

            verifyNoInteractions(mapboxAPIService);

            verifyNoMoreInteractions(travelRepository, routeCalculationService, redisTrackingService);
        }

        public static Stream<Arguments> nullPreviousEtaProvider() {
            return Stream.of(
                    Arguments.of(new PreviousStateDTO(null, 18.5, System.currentTimeMillis())),
                    Arguments.of(new PreviousStateDTO(1200.0, 18.5, null)),
                    Arguments.of((PreviousStateDTO) null)
            );
        }
    }

    @Nested
    class confirmEmbarkOnTravel {

        @Test
        @DisplayName("should confirm student embark on travel with success")
        void shouldConfirmStudentEmbarkOnTravelWithSuccess() {
            UUID studentId = UUID.randomUUID();

            when(studentTravelRepository.findByStudentIdAndTravelId(studentId, travel.getId()))
                    .thenReturn(Optional.of(studentTravel));

            travelTrackingService.confirmEmbarkOnTravel(studentId, travel.getId());

            ArgumentCaptor<StudentTravel> studentTravelCaptor = ArgumentCaptor.forClass(StudentTravel.class);

            verify(studentTravelRepository, times(1)).save(studentTravelCaptor.capture());
            StudentTravel storedValue = studentTravelCaptor.getValue();

            assertTrue(storedValue.isEmbark());
        }

        @ParameterizedTest
        @DisplayName("throw exception when require parameters are null")
        @MethodSource("nullRequireParametersProvider")
        void throwExceptionWhenRequireParametersAreNull(UUID studentId, UUID travelId) {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.confirmEmbarkOnTravel(studentId, travelId));

            verifyNoInteractions(studentTravelRepository);
        }

        public static Stream<Arguments> nullRequireParametersProvider() {
            return Stream.of(
                    Arguments.of(null, UUID.randomUUID()),
                    Arguments.of( UUID.randomUUID(), null),
                    Arguments.of(null, null)
            );
        }

        @Test
        @DisplayName("throw exception when association travel and student not found from database")
        void throwExceptionWhenStudentTravelAssociationNotFound() {
            UUID studentId = UUID.randomUUID();
            when(studentTravelRepository.findByStudentIdAndTravelId(studentId, travel.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(TravelStudentAssociationNotFoundException.class, () -> travelTrackingService.confirmEmbarkOnTravel(studentId, travel.getId()));

            verifyNoMoreInteractions(studentTravelRepository);
        }

        @Test
        @DisplayName("throw exception when student already embark on this trip")
        void throwExceptionWhenStudentAlreadyEmbarkOnTrip() {
            UUID studentId = UUID.randomUUID();
            StudentTravel mockStudentTravel = new StudentTravel(UUID.randomUUID(), travel, new Student(), true, null, null, new GeoPosition());

            when(studentTravelRepository.findByStudentIdAndTravelId(studentId, travel.getId()))
                    .thenReturn(Optional.of(mockStudentTravel));

            assertThrows(BoardingAlreadyConfirmedException.class, () -> travelTrackingService.confirmEmbarkOnTravel(studentId, travel.getId()));

            verifyNoMoreInteractions(studentTravelRepository);
        }
    }

    @Nested
    class getDriverPosition {

        @Test
        @DisplayName("should get driver position with success")
        void shouldGetDriverPositionWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getLiveLocation(travel.getId().toString())).
                    thenReturn(liveLocationDTO);

            when(routeCalculationService.isRouteDeviation(eq(liveLocationDTO.lastCalcLat()), eq(liveLocationDTO.lastCalcLng()), eq(liveLocationDTO.geometry())))
                    .thenReturn(routeDeviationDTO);
            when(mapboxAPIService.calculateRoute(liveLocationDTO.longitude(), liveLocationDTO.latitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);

            LiveLocationDTO result = travelTrackingService.getDriverPosition(travel.getId());

            assertEquals(result.latitude(), liveLocationDTO.latitude());
            assertEquals(result.longitude(), liveLocationDTO.longitude());
            assertEquals(result.geometry(), routeDetailsDTO.geometry());
            assertEquals(result.distance(), routeDetailsDTO.distance());

            verify(redisTrackingService, times(1))
                    .storeLiveLocation(eq(travel.getId().toString()),
                            eq(String.valueOf(liveLocationDTO.latitude())),
                            eq(String.valueOf(liveLocationDTO.longitude())),
                            eq(routeDetailsDTO.distance()),
                            eq(routeDetailsDTO.geometry()));
        }
    }

}