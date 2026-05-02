package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.EmptyMandatoryFieldsFound;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import com.travel_system.backend_app.exceptions.TravelException;
import com.travel_system.backend_app.exceptions.TripNotFound;
import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
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

    private Clock clock;

    VehicleLocationRequestDTO vehicleLocationRequestDTO;
    Travel travel;
    LiveLocationDTO liveLocationDTO;
    NewLocationReceivedEvents newLocationReceivedEvents;

    @BeforeEach
    void setUp() {
        vehicleLocationRequestDTO = new VehicleLocationRequestDTO(UUID.randomUUID(), -12.973456, -38.501234, 60.0, 180.0);
        travel = new Travel(UUID.randomUUID(), new City(UUID.randomUUID(), "Salvador", CitySize.TOWN, true), TravelStatus.PENDING, new Driver(UUID.randomUUID(), "driver@gmail.com", "123456", "João", "Silva", "75999999999", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), "Salvador", 10, new ArrayList<>()), Instant.now(), null, "encoded_polyline", 3600.0, 15.5, -12.973456, -38.501234, -12.985678, -38.512345);
        liveLocationDTO = new LiveLocationDTO(-12.973456, -38.501234, "encoded_polyline_example", 12.5, -12.970000, -38.500000);
        newLocationReceivedEvents = new NewLocationReceivedEvents(UUID.randomUUID(), -12.973456, -38.501234, Instant.now(), TravelStatus.TRAVELLING, 60.0, 180.0);
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

            doNothing().when(redisTrackingService)
                    .storeLiveLocation(
                            eq(travel.getId().toString()),
                            eq(vehicleLocationRequestDTO.latitude().toString()),
                            eq(vehicleLocationRequestDTO.longitude().toString()),
                            eq(liveLocationDTO.distance()),
                            eq(liveLocationDTO.geometry()));

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
            verify(redisTrackingService, times(1)).storeLiveLocation(anyString(), anyString(), anyString(), anyDouble(), anyString());

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
    }
}