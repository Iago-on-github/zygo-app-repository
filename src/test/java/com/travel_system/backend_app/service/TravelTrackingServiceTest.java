package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.NestedTestConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
    private TravelService travelService;

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
    private static final double ROUTE_RECALCULATION_THRESHOLD = 50.0;

    @BeforeEach
    void setUp() {
        vehicleLocationRequestDTO = new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, -38.501234, 60.0, 180.0);
        travel = new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.PENDING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>(), new City()),Instant.now(),  Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345, "Feira de Santana");
        liveLocationDTO = new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000);
        newLocationReceivedEvents = new NewLocationReceivedEvents(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), TravelStatus.TRAVELLING, 60.0, 180.0);
        routeDeviationDTO = new RouteDeviationDTO(25.0, true, -12.972000, -38.500000);
        routeDetailsDTO = new RouteDetailsDTO(2100.0, 35.0, "encoded_polyline_example");
        previousStateDTO = new PreviousStateDTO(1200.0, 18.5, System.currentTimeMillis());
        studentTravel = new StudentTravel(UUID.randomUUID(), travel, new Student(), false, null, null, new GeoPosition(), StudentTravelStatus.ACTIVE);
    }

    @Nested
    class markDriverCheckpoint {
        UUID travelId;
        UUID cityId;

        @BeforeEach
        void setUp() {
            travel.setId(vehicleLocationRequestDTO.travelId());
            cityId = UUID.randomUUID();

            travelId = travel.getId();

            travel.setTravelStatus(TravelStatus.TRAVELLING);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Primeiro contato, ainda não há dados do redis e faz o cálculo inicial")
            void shouldStoreRouteStateWhenReferenceIsNull() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any())).thenReturn(null);
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, times(1)).storeCurrentLocation(any(), any());
                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any());
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

                verify(redisTrackingService, times(1)).getLiveLocation(any());
                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }

            @Test
            void shouldNotRecalculateRouteWhenShouldRecalculateRouteReturnsFalse() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any()))
                        .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(null);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, times(1)).getLiveLocation(any());
                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }

            @Test
            void shouldNotCallMapboxWhenVehicleIsNotOffRouteAndGeometryExists() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any()))
                        .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(any())).thenReturn(routeDetailsDTO);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 5.0);
                when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                        .thenReturn(new RouteDeviationDTO(25.0, false, -12.972000, -38.500000));

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, times(1)).getLiveLocation(any());
                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }

            @Test
            void shouldCallMapboxAndStoreNewStateWhenVehicleIsOffRoute() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any()))
                        .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(any())).thenReturn(routeDetailsDTO);

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 5.0);
                when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                        .thenReturn(routeDeviationDTO);
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any());
                verify(redisTrackingService, times(1)).getLiveLocation(any());
                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }

            @Test
            void shouldCallMapboxWhenGeometryIsNullRegardlessOfOffRouteStatus() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any()))
                        .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(any())).thenReturn(routeDetailsDTO = new RouteDetailsDTO(2100.0, 35.0, null));

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 5.0);
                when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                        .thenReturn(new RouteDeviationDTO(25.0, false, -12.972000, -38.500000));

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any());
                verify(redisTrackingService, times(1)).getLiveLocation(any());

                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }

            @Test
            void shouldNotStoreRouteDataWhenMapboxReturnsNullAndVehicleIsOffRoute() {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any()))
                        .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
                when(redisTrackingService.getLiveLocation(any())).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(any())).thenReturn(routeDetailsDTO = new RouteDetailsDTO(2100.0, 35.0, null));

                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 5.0);
                when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                        .thenReturn(routeDeviationDTO);

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService);

                verify(redisTrackingService, never()).storeCalculatedRouteState(any(), anyString(), anyString(), any());

                verify(redisTrackingService, times(1)).getLiveLocation(any());

                verify(travelService, times(1)).processStudentAwayState(any(), any());
                verify(gpsDataIngestorService, times(1)).sendVehicleGps(any(), any(), any());
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @MethodSource("nullRouteDetailFieldsProvider")
            void shouldNotProceedWhenRouteReferenceIsNullAndMapboxReturnsNull(RouteDetailsDTO routeDetailsDTO) {
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

                when(redisTrackingService.getRouteCalculateReference(any())).thenReturn(null);

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                assertThrows(RecalculateEtaException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));

                verify(redisTrackingService, times(1)).storeCurrentLocation(any(), any());
                verify(redisTrackingService, times(1)).getRouteCalculateReference(any());
                verify(redisTrackingService, times(1)).getRouteState(any());

                verifyNoMoreInteractions(routeCalculationService, mapboxAPIService, redisTrackingService, travelService, gpsDataIngestorService);

            }

            public static Stream<Arguments> nullRouteDetailFieldsProvider() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(null, null, "encoded_geometry_exemple")),
                        Arguments.of(new RouteDetailsDTO(null, 124.2, null)),
                        Arguments.of((RouteDetailsDTO) null)
                );
            }
        }
    }

    @Nested
    class processNewLocation {

        @Test
        @DisplayName("should process new location when isRouteOff returns TRUE with success")
        void shouldProcessNewLocationWhenIsRouteOffReturnsTrueWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));

            when(redisTrackingService.getRouteCalculateReference(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteCalculationReferenceDTO(-12.950000, -38.480000));
            when(redisTrackingService.getRouteState(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteDetailsDTO(null, liveLocationDTO.distance(), liveLocationDTO.geometry()));

            when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                    .thenReturn(routeDeviationDTO);
            when(mapboxAPIService.recalculateETA(vehicleLocationRequestDTO.longitude(), vehicleLocationRequestDTO.latitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocationRequestDTO.latitude()),
                    eq(vehicleLocationRequestDTO.longitude()),
                    eq(-12.950000),
                    eq(-38.480000)))
                    .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 1.0);

            travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

            verify(travelRepository, times(1)).findById(any());
            verify(routeCalculationService, times(1)).isRouteDeviation(any());
            verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());

            verify(redisTrackingService, times(1)).storeCalculatedRouteState(
                    eq(travel.getId()),
                    eq(vehicleLocationRequestDTO.latitude().toString()),
                    eq(vehicleLocationRequestDTO.longitude().toString()),
                    eq(new RouteDetailsDTO(routeDetailsDTO.duration(), routeDetailsDTO.distance(), routeDetailsDTO.geometry()))
            );

            verify(redisTrackingService, times(1)).storeTravelMetadata(
                    eq(travel.getId()),
                    eq(new RouteDetailsDTO(routeDetailsDTO.duration(), routeDetailsDTO.distance(), routeDetailsDTO.geometry())),
                    eq(travel.getTravelStatus().toString())
            );
        }

        @Test
        @DisplayName("should process new location when isRouteOff returns FALSE with success")
        void shouldProcessNewLocationWhenIsRouteOffReturnsFalseWithSuccess() {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            previousStateDTO = new PreviousStateDTO(10.0, 5000.0, FIXED_TIMESTAMP - 1000L);

            when(travelRepository.findById(vehicleLocationRequestDTO.travelId())).thenReturn(Optional.of(travel));

            when(redisTrackingService.getRouteCalculateReference(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
            when(redisTrackingService.getRouteState(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteDetailsDTO(null, liveLocationDTO.distance(), liveLocationDTO.geometry()));
            when(redisTrackingService.getPreviousEta(travel.getId())).thenReturn(previousStateDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocationRequestDTO.latitude()),
                    eq(vehicleLocationRequestDTO.longitude()),
                    eq(liveLocationDTO.lastCalcLat()),
                    eq(liveLocationDTO.lastCalcLng())))
                    .thenReturn(ROUTE_RECALCULATION_THRESHOLD - 5.0);

            when(clock.millis()).thenReturn(FIXED_TIMESTAMP);

            travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

            verify(travelRepository, times(1)).findById(any());

            verifyNoInteractions(mapboxAPIService);

            double expectedEta = 10.0 - 1.0;

            verify(redisTrackingService, never()).storeCalculatedRouteState(any(), anyString(), anyString(), any());

            verify(redisTrackingService, times(1)).storeTravelMetadata(
                    eq(travel.getId()),
                    eq(new RouteDetailsDTO(expectedEta, travel.getDistance(), travel.getPolylineRoute())),
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

            when(redisTrackingService.getRouteCalculateReference(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteCalculationReferenceDTO(-12.950000, -38.480000));
            when(redisTrackingService.getRouteState(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteDetailsDTO(null, liveLocationDTO.distance(), liveLocationDTO.geometry()));

            when(routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(vehicleLocationRequestDTO.travelId(), vehicleLocationRequestDTO.latitude(), vehicleLocationRequestDTO.longitude())))
                    .thenReturn(routeDeviationDTO);
            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocationRequestDTO.latitude()),
                    eq(vehicleLocationRequestDTO.longitude()),
                    eq(-12.950000),
                    eq(-38.480000)))
                    .thenReturn(ROUTE_RECALCULATION_THRESHOLD + 1.0);

            when(mapboxAPIService.recalculateETA(vehicleLocationRequestDTO.longitude(), vehicleLocationRequestDTO.latitude(), travel.getFinalLongitude(), travel.getFinalLatitude()))
                    .thenReturn(routeDetailsDTO);

            assertThrows(RecalculateEtaException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

//            verifyNoInteractions(redisTrackingService);

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

            when(redisTrackingService.getRouteCalculateReference(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteCalculationReferenceDTO(liveLocationDTO.lastCalcLat(), liveLocationDTO.lastCalcLng()));
            when(redisTrackingService.getRouteState(vehicleLocationRequestDTO.travelId()))
                    .thenReturn(new RouteDetailsDTO(null, liveLocationDTO.distance(), liveLocationDTO.geometry()));
            when(redisTrackingService.getPreviousEta(travel.getId())).thenReturn(previousStateDTO);

            when(routeCalculationService.calculateHaversineDistanceInMeters(
                    eq(vehicleLocationRequestDTO.latitude()),
                    eq(vehicleLocationRequestDTO.longitude()),
                    eq(liveLocationDTO.lastCalcLat()),
                    eq(liveLocationDTO.lastCalcLng())))
                    .thenReturn(ROUTE_RECALCULATION_THRESHOLD - 5.0);

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
            StudentTravel mockStudentTravel = new StudentTravel(UUID.randomUUID(), travel, new Student(), true, null, null, new GeoPosition(), StudentTravelStatus.ACTIVE);

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
            when(redisTrackingService.getLiveLocation(travel.getId())).
                    thenReturn(liveLocationDTO);

            LiveLocationDTO result = travelTrackingService.getDriverPosition(travel.getId());

            System.out.println("result: " + result);

            assertEquals(result.latitude(), liveLocationDTO.latitude());
            assertEquals(result.longitude(), liveLocationDTO.longitude());
            assertEquals(result.geometry(), liveLocationDTO.geometry());
            assertEquals(result.distance(), liveLocationDTO.distance());

            verify(redisTrackingService, times(1)).getLiveLocation(eq(travel.getId()));

        }

        @Test
        @DisplayName("throw exception when require parameter data is null")
        void throwExceptionWhenRequireParameterIsNull() {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.getDriverPosition(null));

            verifyNoInteractions(
                    travelRepository,
                    redisTrackingService
            );
        }

        @Test
        @DisplayName("throw exception when travel not found from database")
        void throwExceptionWhenTravelNotFound() {
            when(travelRepository.findById(travel.getId())).thenReturn(Optional.empty());

            assertThrows(TripNotFound.class, () -> travelTrackingService.getDriverPosition(travel.getId()));

            verifyNoInteractions(
                    routeCalculationService,
                    redisTrackingService,
                    mapboxAPIService
            );

            verifyNoMoreInteractions(travelRepository);
        }

        @ParameterizedTest
        @DisplayName("throw exception when travel is not travelling")
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) {
            travel.setTravelStatus(travelStatus);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));

            assertThrows(TravelException.class, () -> travelTrackingService.getDriverPosition(travel.getId()));

            verifyNoInteractions(redisTrackingService);

            verifyNoMoreInteractions(travelRepository);

        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.PENDING),
                    Arguments.of(TravelStatus.FINISH)
            );
        }

        @ParameterizedTest
        @DisplayName("throw exception when 'getLiveLocation' parameters returns null")
        @MethodSource("nullLiveLocationProvider")
        void throwExceptionWhenGetLiveLocationParametersReturnsNull(LiveLocationDTO liveLocation) {
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getLiveLocation(travel.getId())).thenReturn(liveLocation);

            assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.getDriverPosition(travel.getId()));

            verifyNoInteractions(routeCalculationService, mapboxAPIService);
            verifyNoMoreInteractions(travelRepository, redisTrackingService);
        }

        public static Stream<Arguments> nullLiveLocationProvider() {
            return Stream.of(
                    Arguments.of(new LiveLocationDTO(null, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000)),
                    Arguments.of(new LiveLocationDTO(-12.973456, null, "encoded_polyline_example", 12.5, -12.970000, -38.500000)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", null, -12.970000, -38.500000)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, null, -38.500000)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, null)),
                    Arguments.of((LiveLocationDTO) null)
            );
        }
    }

    @Nested
    class getTravelHistory {

        @Test
        @DisplayName("should get travel history with success")
        void shouldGetTravelHistoryWithSuccess() {
            LocationPointDTO locationPointDTO = new LocationPointDTO(-12.973456, -38.501234, Instant.now());

            Page<LocationPointDTO> pageLocation = new PageImpl<>(List.of(locationPointDTO));

            when(travelLocationHistoryRepository.findLatLongByTravelIdAsc(eq(travel.getId()), any(Pageable.class)))
                    .thenReturn(pageLocation);

            Page<LocationPointDTO> result = travelTrackingService.getTravelHistory(travel.getId());

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(locationPointDTO.latitude(), result.getContent().getFirst().latitude());
            assertEquals(locationPointDTO.longitude(), result.getContent().getFirst().longitude());

            verify(travelLocationHistoryRepository, times(1))
                    .findLatLongByTravelIdAsc(eq(travel.getId()), any(Pageable.class));
        }

        @Test
        @DisplayName("throw exception when require parameter data not found")
        void throwExceptionWhenRequireParameterDataNotFound() {
            assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.getTravelHistory(null));

            verifyNoInteractions(travelLocationHistoryRepository);
        }
    }

}