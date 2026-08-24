package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private LocationService locationService;
    @Mock
    private TravelCacheService travelCacheService;

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
    Driver driver;
    Customer customer;

    private static final long FIXED_TIMESTAMP = 1_700_000_000_000L;
    private static final double ROUTE_RECALCULATION_THRESHOLD = 50.0;

   @BeforeEach
    void setUp() {
        customer = new Customer(UUID.randomUUID(), "Universidade Exemplo", "universidade-exemplo", "12.345.678/0001-90", true, new City(), ClientSector.PRIVATE_CLIENT, "https://cdn.exemplo.com/customers/universidade-exemplo.png", Instant.parse("2026-07-16T12:00:00Z"), Instant.parse("2026-07-16T12:00:00Z"));
        driver = new Driver(UUID.randomUUID(), "joao.silva@exemplo.com", "Senha@123", "João", "Silva", "+55 11 98888-7777", "https://cdn.exemplo.com/drivers/joao-silva.png", GeneralStatus.ACTIVE, LocalDateTime.of(2026, 7, 16, 12, 30), LocalDateTime.of(2026, 7, 16, 12, 30), customer, "Transporte Escolar", 24);
        travel = new Travel(UUID.randomUUID(), TravelStatus.TRAVELLING, driver, Instant.parse("2026-07-16T10:00:00Z"), Instant.parse("2026-07-16T10:10:00Z"), TravelPeriod.MORNING, null, "encoded_polyline_exemplo", 35.5, 18.2, -23.550520, -46.633308, -23.548900, -46.630000, "São Paulo", customer, null);
        vehicleLocationRequestDTO = new VehicleLocationRequestDTO(travel.getId(), -12.973456, -38.501234, 60.0, 180.0);
        liveLocationDTO = new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000, null);
        newLocationReceivedEvents = new NewLocationReceivedEvents(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), TravelStatus.TRAVELLING, 60.0, 180.0);
        routeDeviationDTO = new RouteDeviationDTO(25.0, true, -12.972000, -38.500000);
        routeDetailsDTO = new RouteDetailsDTO(2100.0, 35.0, "encoded_polyline_example");
        previousStateDTO = new PreviousStateDTO(1200.0, 18.5, System.currentTimeMillis());
        studentTravel = new StudentTravel(UUID.randomUUID(), travel, new Student(), false, null, null, new GeoPosition(), StudentTravelStatus.ACTIVE);
    }

    @Nested
    class markDriverCheckpoint {
        TravelCacheDTO travelCacheDTO;
        RouteDetailsDTO routeDetailsDTO;
        RouteCalculationReferenceDTO routeCalculationReferenceDTO;
        CurrentVehicleLocationDTO currentVehicleLocationDTO;

        UUID travelId;
        UUID cityId;

        @BeforeEach
        void setUp() {
            travel.setId(vehicleLocationRequestDTO.travelId());
            cityId = UUID.randomUUID();

            travelId = travel.getId();

            travel.setTravelStatus(TravelStatus.TRAVELLING);

            travelCacheDTO = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), TravelStatus.TRAVELLING, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);
            routeDetailsDTO = new RouteDetailsDTO(300.0, 1200.0, "encodedPolylineHere");
            routeCalculationReferenceDTO = new RouteCalculationReferenceDTO(-32.223, 12.323);
            currentVehicleLocationDTO = new CurrentVehicleLocationDTO(-32.223, 12.323, 70.3, 3023.1);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve realizar o primeiro processamento de checkpoint com sucesso quando a referência de rota for inexistente")
            void shouldProcessFirstCheckpointWhenRouteCalculationReferenceDoesNotExist() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);

                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(null); // sem ref anterior

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any());

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verifyNoInteractions(routeCalculationService);
                verifyNoMoreInteractions(mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve realizar o processamento do checkpoint com sucesso quando os dados de lat/lng da referência de rota forem inexistentes")
            @MethodSource("latAndLngNullable")
            void shouldProcessFirstCheckpointWhenLastCalculatedLatitudeOrLongitudeIsNull(RouteCalculationReferenceDTO routeCalculationReferenceDTO) {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);

                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO); // com ref anterior, mas com lat/lng null

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any());

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verifyNoInteractions(routeCalculationService);
                verifyNoMoreInteractions(mapboxAPIService);
            }

            public static Stream<Arguments> latAndLngNullable() {
                return Stream.of(
                        Arguments.of(new RouteCalculationReferenceDTO(null, 12.323)),
                        Arguments.of(new RouteCalculationReferenceDTO(-32.223, null))
                );
            }

            @Test
            @DisplayName("Deve garantir que os dados do novo ping sejam enviados corretamente ao cache do redis")
            void shouldStoreCurrentVehicleLocationWhenCheckpointIsReceived() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);

                RouteCalculationReferenceDTO mockReference = new RouteCalculationReferenceDTO(-32.223, 12.323);
                RouteDetailsDTO mockRouteState = new RouteDetailsDTO(120.0, 500.0, "geometry_mock");

                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(mockReference);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(mockRouteState));
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                ArgumentCaptor<CurrentVehicleLocationDTO> locationCaptor = ArgumentCaptor.forClass(CurrentVehicleLocationDTO.class);
                verify(redisTrackingService, times(1)).storeCurrentLocation(eq(travelId), locationCaptor.capture());

                CurrentVehicleLocationDTO capturedDto = locationCaptor.getValue();
                assertNotNull(capturedDto);
                assertEquals(-12.973456, capturedDto.latitude());
                assertEquals(-38.501234, capturedDto.longitude());
                assertEquals(60.0, capturedDto.speed());
                assertEquals(180.0, capturedDto.heading());
            }

            @Test
            @DisplayName("Deve chamar o método que verifica se precisa realizar o recálculo de rota e não deve retornar false para recálculo")
            void shouldContinueWithoutRouteRevalidationWhenDistanceIsBelowThreshold() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(40.0);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verifyNoMoreInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve chamar o método que verifica se precisa realizar o recálculo de rota e deve retornar true para récalculo com o motorista permancendo na rota")
            void shouldCheckRouteDeviationWithoutRecalculatingWhenDriverRemainsOnRoute() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(59.0);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(new RouteDetailsDTO(-32.123, 11.323, "geometry_exemple")));
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(30.0, false, -32.123, 11.323));

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verifyNoMoreInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve chamar o método que verifica se precisa realizar o recálculo de rota e deve retornar true para récalculo com o motorista fora na rota")
            void shouldRecalculateAndStoreRouteWhenDriverIsOffRoute() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(59.0);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(new RouteDetailsDTO(-32.123, 11.323, "geometry_exemple")));

                // offRoute true
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(30.0, true, -32.123, 11.323));

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));
                verifyNoMoreInteractions(mapboxAPIService);
            }

            @Test
            @DisplayName("Deve recalcular a rota quando a Geometry armazenada no Redis for NULL")
            void shouldRecalculateRouteWhenStoredGeometryIsNull() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(59.0);
                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(new RouteDetailsDTO(-32.123, 11.323, null)));

                // offRoute false
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(30.0, false, -32.123, 11.323));

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO);

                ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());

                List<Object> publishedEvents = eventCaptor.getAllValues();

                assertInstanceOf(StudentAwayStateCheckEvent.class, publishedEvents.get(0));
                StudentAwayStateCheckEvent awayEvent = (StudentAwayStateCheckEvent) publishedEvents.get(0);
                assertEquals(travelId, awayEvent.travelId());

                assertInstanceOf(NewLocationReceivedEvents.class, publishedEvents.get(1));
                NewLocationReceivedEvents locationEvent = (NewLocationReceivedEvents) publishedEvents.get(1);
                assertEquals(travelId, locationEvent.travelId());
                assertEquals(vehicleLocationRequestDTO.latitude(), locationEvent.latitude());
                assertEquals(vehicleLocationRequestDTO.longitude(), locationEvent.longitude());

                assertInstanceOf(VehicleGpsMessageDTO.class, publishedEvents.get(2));
                VehicleGpsMessageDTO gpsEvent = (VehicleGpsMessageDTO) publishedEvents.get(2);
                assertEquals(cityId.toString(), gpsEvent.city());
                assertEquals(travelId.toString(), gpsEvent.travelId());

                verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));
                verifyNoMoreInteractions(mapboxAPIService);
            }
        }

        @Nested
        class failureScenarios {

            @Test
            @DisplayName("Deve lançar exception quando o ID do body for diferente do ID da URL")
            void shouldThrowIllegalStateExceptionWhenPathTravelIdDiffersFromBodyTravelId() {
                assertThrows(IllegalStateException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, UUID.randomUUID(), vehicleLocationRequestDTO));

                verifyNoInteractions(travelCacheService, redisTrackingService, mapboxAPIService, eventPublisher);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a viagem não estiver em andamento")
            @MethodSource("travelStatusProvider")
            void shouldThrowTravelExceptionWhenCheckpointTravelIsNotTravelling(TravelStatus travelStatus) {
                TravelCacheDTO travelCache = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), travelStatus, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);

                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);

                assertThrows(TravelException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));
                
                verifyNoMoreInteractions(travelCacheService);
                verifyNoInteractions(redisTrackingService, mapboxAPIService, eventPublisher);
            }
            
            public static Stream<Arguments> travelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.PENDING),
                        Arguments.of(TravelStatus.FINISH),
                        Arguments.of(TravelStatus.CANCELED)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a viagem não tiver os dados de coordenadas válidos ou suficientes")
            @MethodSource("vehicleLocProviderDTO")
            void shouldThrowExceptionWhenCheckpointRequestIsNull(VehicleLocationRequestDTO newVehicleLocationRequestDTO) {
                UUID sameTravelId = newVehicleLocationRequestDTO.travelId();
                
                assertThrows(NoSuchCoordinates.class, () -> travelTrackingService.markDriverCheckpoint(cityId, sameTravelId, newVehicleLocationRequestDTO));

                verifyNoMoreInteractions(travelCacheService);
                verifyNoInteractions(redisTrackingService, mapboxAPIService, eventPublisher);
            }

            public static Stream<Arguments> vehicleLocProviderDTO() {
                UUID travelId = UUID.randomUUID();

                return Stream.of(
                        Arguments.of(new VehicleLocationRequestDTO(travelId, null, -38.501234, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(travelId, -12.973456, null, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(travelId, -12.973456, -38.501234, null, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(travelId, -12.973456, -38.501234, 60.0, null))
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o MAPBOX retornar resposta nula no primeiro checkpoint")
            @MethodSource("mapboxRouteDetailsProvider")
            void shouldThrowRecalculateEtaExceptionWhenMapboxReturnsNullOnFirstCheckpoint(RouteDetailsDTO newRouteDetailsDTO) {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(null);
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(newRouteDetailsDTO);

                assertThrows(RecalculateEtaException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));

                verifyNoMoreInteractions(travelCacheService, mapboxAPIService);
            }

            public static Stream<Arguments> mapboxRouteDetailsProvider() {
                return Stream.of(
                        // duration pode ser aceito como null, já que o cálculo depende do tempo da viagem + a distância, sendo impossível de cálcular quando o trajeto acaba de começar
//                        Arguments.of(new RouteDetailsDTO(null, 1200.0, "encodedPolylineHere")),
                        Arguments.of(new RouteDetailsDTO(300.0, null, "encodedPolylineHere")),
                        Arguments.of(new RouteDetailsDTO(300.0, 1200.0, null)),
                        Arguments.of((RouteDetailsDTO) null)
                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o MAPBOX retornar resposta null no cálculo de desvio")
            void shouldThrowRecalculateEtaExceptionWhenMapboxReturnsNullDuringCheckpointRecalculation() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(59.0);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(new RouteDetailsDTO(-32.123, 11.323, "geometry_exemple")));

                // offRoute true
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(30.0, true, -32.123, 11.323));

                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(null); // api retorna null
                
                assertThrows(RecalculateEtaException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));
                
                verify(redisTrackingService, times(1)).storeCurrentLocation(any(), any(CurrentVehicleLocationDTO.class));
                verify(routeCalculationService, times(1)).isRouteDeviation(any(RouteDeviationRequestDTO.class));
                
                verify(redisTrackingService, never()).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));
                
                verifyNoInteractions(eventPublisher);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a localização não for encontrada no redis")
            @MethodSource("redisLocalizationProvider")
            void shouldThrowLiveLocationDataNotFoundExceptionWhenLiveLocationDoesNotExistAfterCheckpoint(LiveLocationDTO invalidRedisLoc) {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(59.0);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(new RouteDetailsDTO(-32.123, 11.323, "geometry_exemple")));
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(30.0, false, -32.123, 11.323));

                when(redisTrackingService.getLiveLocation(travelId)).thenReturn(invalidRedisLoc);

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequestDTO));

                verifyNoInteractions(eventPublisher);
            }

            public static Stream<Arguments> redisLocalizationProvider() {
                return Stream.of(
                        Arguments.of(new LiveLocationDTO(null, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000, null)),
                        Arguments.of(new LiveLocationDTO(-12.973456, null, "encoded_polyline_example", 12.5, -12.970000, -38.500000, null)),
                        Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", null, -12.970000, -38.500000, null)),
                        Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, null, -38.500000, null)),
                        Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, null, null)),
                        Arguments.of((LiveLocationDTO) null)
                );
            }
        }
    }

    @Nested
    class processNewLocation {
        TravelCacheDTO travelCacheDTO;
        RouteDetailsDTO routeDetailsDTO;
        RouteCalculationReferenceDTO routeCalculationReferenceDTO;
        CurrentVehicleLocationDTO currentVehicleLocationDTO;

        UUID travelId;
        UUID cityId;

        @BeforeEach
        void setUp() {
            travel.setId(vehicleLocationRequestDTO.travelId());
            cityId = UUID.randomUUID();

            travelId = travel.getId();

            travel.setTravelStatus(TravelStatus.TRAVELLING);

            travelCacheDTO = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), TravelStatus.TRAVELLING, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);
            routeDetailsDTO = new RouteDetailsDTO(300.0, 1200.0, "encodedPolylineHere");
            routeCalculationReferenceDTO = new RouteCalculationReferenceDTO(-32.223, 12.323);
            currentVehicleLocationDTO = new CurrentVehicleLocationDTO(-32.223, 12.323, 70.3, 3023.1);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve processar localização sem necessidade de revalidar a rota")
            void shouldCalculateEtaInternallyWhenRouteRevalidationIsNotRequired() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousStateDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(40.0); // dentro da rota

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                verify(redisTrackingService, times(1)).storeTravelMetadata(any(), any(RouteDetailsDTO.class), any());

                verify(redisTrackingService, never()).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));
                verify(routeCalculationService, never()).isRouteDeviation(any(RouteDeviationRequestDTO.class));
               }

            @Test
            @DisplayName("Deve revalidar a rota, mas motorista permanece na rota")
            void shouldCalculateEtaInternallyWhenDriverRemainsOnRouteAfterRevalidation() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getPreviousEta(travelId)).thenReturn(previousStateDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.0); // fora da rota
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(0.0, false, 0.0, 0.0));

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                verify(routeCalculationService, times(1)).isRouteDeviation(any(RouteDeviationRequestDTO.class));
                verify(redisTrackingService, times(1)).storeTravelMetadata(any(), any(RouteDetailsDTO.class), any());

                verify(mapboxAPIService, never()).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(redisTrackingService, never()).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));

            }

            @Test
            @DisplayName("Deve revalidar a rota e detectar o desvio")
            void shouldRecalculateEtaUsingMapboxWhenDriverIsOffRoute() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.0); // fora da rota
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(0.0, true, 0.0, 0.0));

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                verify(routeCalculationService, times(1)).isRouteDeviation(any(RouteDeviationRequestDTO.class));
                verify(redisTrackingService, times(1)).storeTravelMetadata(any(), any(RouteDetailsDTO.class), any());
                verify(mapboxAPIService, times(1)).recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble());
                verify(redisTrackingService, times(1)).storeCalculatedRouteState(any(), anyString(), anyString(), any(RouteDetailsDTO.class));

            }

            @Test
            @DisplayName("Deve persistir a nova rota calculada no redis")
            void shouldStoreCalculatedRouteStateWhenRouteIsRecalculated() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.0); // fora da rota
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(0.0, true, 0.0, 0.0));

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                verify(redisTrackingService, times(1)).storeCalculatedRouteState(
                        eq(travelCacheDTO.travelId()),
                        eq(vehicleLocationRequestDTO.latitude().toString()),
                        eq(vehicleLocationRequestDTO.longitude().toString()),
                        argThat(routeDetails -> routeDetails.duration().equals(routeDetailsDTO.duration()) &&
                                routeDetails.distance().equals(routeDetailsDTO.distance()) &&
                                routeDetails.geometry().equals(routeDetailsDTO.geometry())));

            }

            @Test
            @DisplayName("Deve persistir os metadados da viagem no redis")
            void shouldStoreTravelMetadataAfterLocationProcessing() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.0); // fora da rota
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(0.0, true, 0.0, 0.0));

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                ArgumentCaptor<RouteDetailsDTO> routeDetailsArgCaptor = ArgumentCaptor.forClass(RouteDetailsDTO.class);

                verify(redisTrackingService, times(1)).storeTravelMetadata(
                        eq(travelCacheDTO.travelId()),
                        routeDetailsArgCaptor.capture(),
                        eq(travelCacheDTO.travelStatus().toString()));

                RouteDetailsDTO result = routeDetailsArgCaptor.getValue();
                assertEquals(result.distance(), routeDetailsDTO.distance());
                assertEquals(result.geometry(), routeDetailsDTO.geometry());
                assertEquals(result.duration(), routeDetailsDTO.duration());
            }

//            @Test
//            @DisplayName("Deve utilizar a distância estática da viagem (travelCache) durante o cálculo interno")
//            void shouldUseTravelStaticDistanceWhenCalculatingEtaInternally() {
//
//            }

            @Test
            @DisplayName("Deve utilizar corretamente as coordenadas finais da viagem no recálculo")
            void shouldUseTravelDestinationCoordinatesWhenRecalculatingEta() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(60.0); // fora da rota
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(routeDetailsDTO);
                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class)))
                        .thenReturn(new RouteDeviationDTO(0.0, true, 0.0, 0.0));
                when(mapboxAPIService.recalculateETA(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(routeDetailsDTO);

                travelTrackingService.processNewLocation(vehicleLocationRequestDTO);

                verify(mapboxAPIService, times(1)).recalculateETA(
                        eq(vehicleLocationRequestDTO.longitude()),
                        eq(vehicleLocationRequestDTO.latitude()),
                        eq(travelCacheDTO.finalLongitude()),
                        eq(travelCacheDTO.finalLatitude()));

            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o DTO de entrada por inválido")
            @MethodSource("vehicleLocRequestProvider")
            void shouldThrowEmptyMandatoryFieldsFoundWhenRequestContainsInvalidMandatoryFields(VehicleLocationRequestDTO newVehicleLocationRequestDTO) {
                assertThrows(EmptyMandatoryFieldsFound.class, () -> travelTrackingService.processNewLocation(newVehicleLocationRequestDTO));

                verifyNoInteractions(travelCacheService, redisTrackingService, routeCalculationService, mapboxAPIService);
            }

            public static Stream<Arguments> vehicleLocRequestProvider() {
                return Stream.of(
                        Arguments.of(new VehicleLocationRequestDTO(null, -12.973456, -38.501234, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), null, -38.501234, 60.0, 180.0)),
                        Arguments.of(new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, null, 60.0, 180.0))
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a viagem não estiver em andamento")
            @MethodSource("travelStatusProvider")
            void shouldThrowTravelExceptionWhenTravelIsNotTravelling(TravelStatus travelStatus) {
                TravelCacheDTO travelCacheWithDynamicSatus = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), travelStatus, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);

                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheWithDynamicSatus);

                assertThrows(TravelException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoMoreInteractions(travelCacheService);

                verifyNoInteractions(redisTrackingService, routeCalculationService, mapboxAPIService);
            }

            public static Stream<Arguments> travelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.FINISH),
                        Arguments.of(TravelStatus.CANCELED),
                        Arguments.of(TravelStatus.PENDING)
                );
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando a referência de cálculo de rota for inválida")
            @MethodSource("invalidRouteReferenceProvider")
            void shouldThrowLiveLocationDataNotFoundExceptionWhenRouteCalculationReferenceIsInvalid(RouteCalculationReferenceDTO newRouteCalcRefDTO) {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(newRouteCalcRefDTO);

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoMoreInteractions(travelCacheService);

                verifyNoInteractions(routeCalculationService, mapboxAPIService);
            }

            public static Stream<Arguments> invalidRouteReferenceProvider() {
                return Stream.of(
                        Arguments.of(new RouteCalculationReferenceDTO(-12.342, null)),
                        Arguments.of(new RouteCalculationReferenceDTO(null, 39.342))

                );
            }

            @Test
            @DisplayName("Deve lançar exception quando o estado da rota for inexistente")
            void shouldThrowLiveLocationDataNotFoundExceptionWhenRouteStateDoesNotExist() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.empty());

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoMoreInteractions(redisTrackingService, travelCacheService);
                verifyNoInteractions(routeCalculationService, mapboxAPIService);
            }

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o estado da rota estiver com dados requeridos ausentes")
            @MethodSource("invalidRouteStateProviderDTO")
            void shouldThrowLiveLocationDataNotFoundExceptionWhenRouteStateContainsInvalidData(RouteDetailsDTO newRouteDetailsDTO) {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(newRouteDetailsDTO));

                assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoMoreInteractions(redisTrackingService, travelCacheService);
                verifyNoInteractions(routeCalculationService, mapboxAPIService);

            }

            public static Stream<Arguments> invalidRouteStateProviderDTO() {
                return Stream.of(
                        Arguments.of(new RouteDetailsDTO(30.0, null, "geometry_teste")),
                        Arguments.of(new RouteDetailsDTO(30.0, 2000.0, null))

                );
            }

            @Test
            @DisplayName("Deve lançar exception quando houver falha ao verificar desvios de rota")
            void shouldPropagateExceptionWhenRouteDeviationVerificationFails() {
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
                when(redisTrackingService.getRouteCalculateReference(travelId)).thenReturn(routeCalculationReferenceDTO);
                when(redisTrackingService.getRouteState(travelId)).thenReturn(Optional.of(routeDetailsDTO));
                when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(54.0);

                when(routeCalculationService.isRouteDeviation(any(RouteDeviationRequestDTO.class))).thenThrow(RuntimeException.class);

                assertThrows(RuntimeException.class, () -> travelTrackingService.processNewLocation(vehicleLocationRequestDTO));

                verifyNoMoreInteractions(redisTrackingService);

            }
        }
    }

/*    @Nested
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
    }*/

    @Nested
    class getDriverPosition {
        TravelCacheDTO travelCacheDTO;

        UUID travelId;
        UUID cityId;

        @BeforeEach
        void setUp() {
            travel.setId(vehicleLocationRequestDTO.travelId());
            cityId = UUID.randomUUID();

            travelId = travel.getId();
            travel.setTravelStatus(TravelStatus.TRAVELLING);

            travelCacheDTO = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), TravelStatus.TRAVELLING, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);
        }

        @Test
        @DisplayName("Deve recuperar a posição do motorista com sucesso")
        void shouldGetDriverPositionWithSuccess() {
            when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
            when(redisTrackingService.getLiveLocation(travelId)).thenReturn(liveLocationDTO);

            LiveLocationDTO result = travelTrackingService.getDriverPosition(travelId);

            assertNotNull(result);

            assertEquals(liveLocationDTO.distance(), result.distance());
            assertEquals(liveLocationDTO.geometry(), result.geometry());
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando a viagem não estiver em andamento")
        @MethodSource("travelStatusProvider")
        void throwExceptionWhenTravelIsNotTravelling(TravelStatus invalidTravelStatus) {
            TravelCacheDTO travelCacheWithInvalidStatus = new TravelCacheDTO(UUID.randomUUID(), cityId, customer.getId(), invalidTravelStatus, -12.9714, -38.5014, "encodedPolylineHere", 12.7, 25.0);

            when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheWithInvalidStatus);

            assertThrows(TravelException.class, () -> travelTrackingService.getDriverPosition(travelId));

            verifyNoInteractions(redisTrackingService);
        }

        public static Stream<Arguments> travelStatusProvider() {
            return Stream.of(
                    Arguments.of(TravelStatus.FINISH),
                    Arguments.of(TravelStatus.CANCELED),
                    Arguments.of(TravelStatus.PENDING)
            );
        }

        @ParameterizedTest
        @DisplayName("Deve lançar exception quando não houver localização no redis")
        @MethodSource("redisLocalizationProvider")
        void throwExceptionWhenRedisLiveLocationNotFoundData(LiveLocationDTO redisLiveLocDTO) {
            when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCacheDTO);
            when(redisTrackingService.getLiveLocation(travelId)).thenReturn(redisLiveLocDTO);

            assertThrows(LiveLocationDataNotFoundException.class, () -> travelTrackingService.getDriverPosition(travelId));

        }

        public static Stream<Arguments> redisLocalizationProvider() {
            return Stream.of(
                    Arguments.of(new LiveLocationDTO(null, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000, null)),
                    Arguments.of(new LiveLocationDTO(-12.973456, null, "encoded_polyline_example", 12.5, -12.970000, -38.500000, null)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", null, -12.970000, -38.500000, null)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, null, -38.500000, null)),
                    Arguments.of(new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, null, null)),
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