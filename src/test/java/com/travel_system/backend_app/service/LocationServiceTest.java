package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.exceptions.TravelException;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.StudentAwayStateDTO;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @InjectMocks
    @Spy
    private LocationService locationService;

    @Mock
    private GeoPositionRepository geoPositionRepository;
    @Mock
    private StudentTravelRepository studentTravelRepository;
    @Mock
    private RouteCalculationService routeCalculationService;
    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private TravelRepository travelRepository;
    @Mock
    private TravelCacheService travelCacheService;
    @Mock
    private TravelTrackingNotificationService trackingNotificationService;
    @Mock
    private TravelService travelService;

    private ArgumentCaptor<GeoPosition> geoPosCaptor = ArgumentCaptor.forClass(GeoPosition.class);

    @Nested
    class updateStudentPosition {

        @Test
        @DisplayName("should create new geo position when student has no previous position")
        void shouldCreateNewGeoPositionWhenStudentHasNoPreviousPosition() {
            // arrange
            StudentTravel studentTravel = new StudentTravel();
            studentTravel.setPosition(null);

            UUID studentId = UUID.randomUUID();
            LiveCoordinates coords = new LiveCoordinates(-12.373, -19.372);

            when(studentTravelRepository.findById(studentId)).thenReturn(Optional.of(studentTravel));

            // act
            locationService.updateStudentPosition(studentId, coords);

            // assert
            verify(geoPositionRepository, times(1)).save(geoPosCaptor.capture());
            GeoPosition savedGeoPos = geoPosCaptor.getValue();
            assertEquals(coords.latitude(), savedGeoPos.getLatitude());
            assertEquals(coords.longitude(), savedGeoPos.getLongitude());
            assertNotNull(savedGeoPos.getTimeStamp());
            assertEquals(studentTravel, savedGeoPos.getStudentTravel());


            assertNotNull(studentTravel.getPosition());
            assertEquals(coords.latitude(), studentTravel.getPosition().getLatitude());
            assertEquals(coords.longitude(), studentTravel.getPosition().getLongitude());

            verifyNoInteractions(routeCalculationService);
        }

        @Test
        @DisplayName("should update the last position when student has previous displacement")
        void shouldUpdateTheLastGeoPositionWhenStudentDisplacement() {
            // arrange
            GeoPosition anteriorPosition = new GeoPosition();
            anteriorPosition.setLatitude(-12.000);
            anteriorPosition.setLongitude(-19.000);
            anteriorPosition.setTimeStamp(Instant.now());

            StudentTravel studentTravel = new StudentTravel();
            studentTravel.setPosition(anteriorPosition);

            UUID studentId = UUID.randomUUID();
            LiveCoordinates coords = new LiveCoordinates(-12.373, -19.372);

            when(studentTravelRepository.findById(studentId)).thenReturn(Optional.of(studentTravel));
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10.0);

            // act
            locationService.updateStudentPosition(studentId, coords);

            // asserts
            assertEquals(coords.latitude(), anteriorPosition.getLatitude());
            assertEquals(coords.longitude(), anteriorPosition.getLongitude());
            assertNotNull(anteriorPosition.getTimeStamp());

            verifyNoInteractions(geoPositionRepository);
        }

        @Test
        @DisplayName("throw exception when student travel not found from database")
        void throwExceptionWhenStudentTravelNotFound() {
            UUID studentTravelId = UUID.randomUUID();
            LiveCoordinates coords = new LiveCoordinates(-12.373, -19.372);

            when(studentTravelRepository.findById(studentTravelId)).thenReturn(Optional.empty());

            // act & assert
            assertThrows(EntityNotFoundException.class, () -> locationService.updateStudentPosition(studentTravelId, coords));

            verify(studentTravelRepository, times(1)).findById(studentTravelId);
            verifyNoInteractions(geoPositionRepository);
            verifyNoInteractions(routeCalculationService);
        }

        @Test
        @DisplayName("should not update position when displacement is below tolerance")
        void shouldNotUpdatePositionWhenDisplacementIsBelowTolerance() {
            // arrange
            GeoPosition anteriorPosition = new GeoPosition();
            anteriorPosition.setLatitude(-12.000);
            anteriorPosition.setLongitude(-19.000);

            UUID studentTravelId = UUID.randomUUID();
            StudentTravel studentTravel = new StudentTravel();
            studentTravel.setPosition(anteriorPosition);

            LiveCoordinates coords = new LiveCoordinates(-12.373, -19.372);

            when(studentTravelRepository.findById(studentTravelId)).thenReturn(Optional.of(studentTravel));
            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(1.0);

            // act
            locationService.updateStudentPosition(studentTravelId, coords);

            // assert - posição anterior não foi modificada.
            assertEquals(-12.000, anteriorPosition.getLatitude());
            assertEquals(-19.000, anteriorPosition.getLongitude());

            verifyNoInteractions(geoPositionRepository);
        }

        @ParameterizedTest
        @DisplayName("should return silently when coordinates data are null")
        @MethodSource("nullCoordsProvider")
        void shouldReturnSilentlyIfCoordinatesAreNull(UUID studentTravelId, LiveCoordinates liveCoordinates) {
            locationService.updateStudentPosition(studentTravelId, liveCoordinates);

            verifyNoInteractions(geoPositionRepository);
            verifyNoInteractions(routeCalculationService);
            verifyNoInteractions(studentTravelRepository);
        }

        public static Stream<Arguments> nullCoordsProvider() {
            return Stream.of(
                    Arguments.of(UUID.randomUUID(), new LiveCoordinates(null, -12.323)),
                    Arguments.of(UUID.randomUUID(), new LiveCoordinates(-18.322, null))
            );
        }
    }

/*    @Nested
    class processStudentAwayState {
        UUID travelId;
        UUID studentId;
        Travel travelEntity;
        Student studentEntity;
        StudentTravel studentTravelEntity;
        LiveLocationDTO liveLocationDTO;
        DistanceResponseDTO distanceResponse;
        StudentAwayStateCheckEvent studentAwayStateCheckEvent;
        StudentAwayStateDTO studentAwayStateDTO;
        TravelCacheDTO travelCache;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentId = UUID.randomUUID();

            studentEntity = new Student();
            studentEntity.setId(studentId);
            studentEntity.setEmail("student@email.com");

            GeoPosition position = new GeoPosition(
                    UUID.randomUUID(),
                    -23.55,
                    -46.63,
                    Instant.now(),
                    null
            );

            studentTravelEntity = new StudentTravel();
            studentTravelEntity.setId(UUID.randomUUID());
            studentTravelEntity.setStudent(studentEntity);
            studentTravelEntity.setPosition(position);
            studentTravelEntity.setEmbark(true);
            studentTravelEntity.setStudentTravelStatus(StudentTravelStatus.ACTIVE);

            travelEntity = new Travel();
            travelEntity.setId(travelId);
            travelEntity.setStudentTravels(new HashSet<>(Set.of(studentTravelEntity)));
            travelEntity.setTravelStatus(TravelStatus.TRAVELLING);

            liveLocationDTO = new LiveLocationDTO(-23.55, -46.63, null, null, null, null, Instant.now());
            distanceResponse = new DistanceResponseDTO(studentId, 400.0);

            studentAwayStateCheckEvent = new StudentAwayStateCheckEvent(travelId, liveLocationDTO);

            studentAwayStateDTO = new StudentAwayStateDTO(studentTravelEntity.getId(), studentId, "emailTeste@student.com", StudentTravelStatus.ACTIVE, true);

            travelCache = new TravelCacheDTO(travelId, null, null, TravelStatus.TRAVELLING, null, null, "encoded<polyline", 3000.0, 33.2);
        }

        @Nested
        class successScenarios {

            @Test
            @DisplayName("Deve marcar o estudante como distante do ônibus SEM dados (histórico) no redis")
            void shouldMarkStudentAwayWithoutRedisData() {
                StudentAwayStateDTO studentAwayStateDTO = new StudentAwayStateDTO(studentTravelEntity.getId(), studentId, "emailTeste@student.com", StudentTravelStatus.ACTIVE, true);
                TravelCacheDTO travelCache = new TravelCacheDTO(travelId, null, null, TravelStatus.TRAVELLING, null, null, "encoded_polyline_route", 3000.3, 23.1);

                doReturn(List.of(distanceResponse)).when(locationService).distanceBetweenPositions(travelId, liveLocationDTO);
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);
                when(redisTrackingService.getStudentAwayTimestamp(travelId)).thenReturn(Collections.emptyMap());

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(redisTrackingService, times(1)).getStudentAwayTimestamp(eq(travelId));
                verify(redisTrackingService, times(1)).markStudentAsAway(eq(travelId), argThat(map ->
                        map.containsKey(studentId) && map.get(studentId) != null
                ));
                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));
                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(studentTravelRepository, times(1)).updateStudentTravelStatus(List.of(studentTravelEntity.getId()), StudentTravelStatus.AWAY_FROM_BUS);

                verify(studentTravelRepository, never()).disconnectedStudentFromTrip(any(), any(), any(), anyBoolean());
                verifyNoInteractions(trackingNotificationService);
            }

            @Test
            @DisplayName("Deve realizar a desconexão automática por tempo de afastamento excedido (maior ou igual a 5 minutos)")
            void shouldAutoDisconnectStudentWhenAwayTimeExceedsFiveMinutes() {
                DistanceResponseDTO distanceResponseDTO = new DistanceResponseDTO(studentId, 350.0);
                Map<UUID, Long> redisMockData = Map.of(studentId, 1719876000000L);

                doReturn(List.of(distanceResponseDTO)).when(locationService).distanceBetweenPositions(travelId, liveLocationDTO);
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);
                when(redisTrackingService.getStudentAwayTimestamp(travelId)).thenReturn(redisMockData);
                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(redisTrackingService, times(1)).getStudentAwayTimestamp(eq(travelId));

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));
                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(studentTravelRepository, times(1)).updateStudentTravelStatus(List.of(studentTravelEntity.getId()), StudentTravelStatus.AWAY_FROM_BUS);

                verify(studentTravelRepository, times(1)).disconnectedStudentFromTrip(any(), any(), any(), anyBoolean());
                verify(trackingNotificationService, times(1)).sendAutoDisconnectStudentNotification(any(), any());

            }

            @Test
            @DisplayName("Deve manter o estado de distância do aluno quando o tempo for inferir ao tempo tolerável (5 minutos)")
            void shouldKeepStudentAwayWhenAwayTimeIsBelowAutoDisconnectThreshold() {
                long threeMinutesAgoMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
                Map<UUID, Long> redisMockData = Map.of(studentId, threeMinutesAgoMillis);

                doReturn(List.of(distanceResponse)).when(locationService).distanceBetweenPositions(travelId, liveLocationDTO);
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);
                when(redisTrackingService.getStudentAwayTimestamp(travelId)).thenReturn(redisMockData);

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(redisTrackingService, times(1)).getStudentAwayTimestamp(eq(travelId));
                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));
                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));

                verify(studentTravelRepository, never()).disconnectedStudentFromTrip(any(), any(), any(), anyBoolean());

                verifyNoInteractions(trackingNotificationService);
            }

            @Test
            @DisplayName("Deve limpar o redis quando o estudante retornar ao perímetro seguro")
            void shouldClearAwayStateWhenStudentReturnsToSafePerimeter() {
                DistanceResponseDTO distanceResponseDTO = new DistanceResponseDTO(studentId, 250.0);

                doReturn(List.of(distanceResponseDTO)).when(locationService).distanceBetweenPositions(travelId, liveLocationDTO);
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);
                doNothing().when(redisTrackingService).clearStudentAwayState(any(), any());

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(redisTrackingService, times(1)).getStudentAwayTimestamp(eq(travelId));

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));
                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));

                verify(studentTravelRepository, never()).disconnectedStudentFromTrip(any(), any(), any(), anyBoolean());
                verify(trackingNotificationService, never()).sendAutoDisconnectStudentNotification(any(), any());
            }
        }

        @Nested
        class failureScenarios {

            @ParameterizedTest
            @DisplayName("Deve lançar exception quando o status da viagem não for compatível")
            @MethodSource("travelStatusProvider")
            void throwTravelExceptionWhenTravelStatusIsNotTravelling(TravelStatus travelStatus) {
                TravelCacheDTO travelCache = new TravelCacheDTO(travelId, null, null, travelStatus, null, null, null, null, null);

                doReturn(List.of(distanceResponse)).when(locationService).distanceBetweenPositions(eq(travelId), eq(liveLocationDTO));
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);

                assertThrows(TravelException.class, () -> locationService.processStudentAwayState(studentAwayStateCheckEvent));

                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));

                verifyNoMoreInteractions(studentTravelRepository);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(trackingNotificationService);

            }

            public static Stream<Arguments> travelStatusProvider() {
                return Stream.of(
                        Arguments.of(TravelStatus.PENDING),
                        Arguments.of(TravelStatus.FINISH),
                        Arguments.of(TravelStatus.CANCELED)
                );
            }

            @Test
            @DisplayName("Deve retornar de forma silenciosa e subir logging caso não haja estudantes elegíveis para a validação")
            void shouldSkipProcessingWhenNoEligibleStudentsAreFound() {
                doReturn(List.of(distanceResponse)).when(locationService).distanceBetweenPositions(eq(travelId), eq(liveLocationDTO));

                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(Collections.emptyList());
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));

                verifyNoMoreInteractions(studentTravelRepository);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(trackingNotificationService);
            }

            @ParameterizedTest
            @DisplayName("Deve retornar de forma silenciosa e subir logging caso o estudante esteja com Status LEFT ou AUTO_DISCONNECTED")
            @MethodSource("studentTravelStatusProvider")
            void shouldSkipStudentsWithLeftOrAutoDisconnectedStatus(StudentTravelStatus studentTravelStatus) {
                StudentAwayStateDTO studentsOnTrip = new StudentAwayStateDTO(studentTravelEntity.getId(), studentId, "emailTeste@student.com",studentTravelStatus, true);

                doReturn(List.of(distanceResponse)).when(locationService).distanceBetweenPositions(eq(travelId), eq(liveLocationDTO));

                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentsOnTrip));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);

                locationService.processStudentAwayState(studentAwayStateCheckEvent);

                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));

                verifyNoMoreInteractions(studentTravelRepository);
                verifyNoInteractions(trackingNotificationService);
            }

            public static Stream<Arguments> studentTravelStatusProvider() {
                return Stream.of(
                        Arguments.of(StudentTravelStatus.LEFT),
                        Arguments.of(StudentTravelStatus.AUTO_DISCONNECTED)
                );
            }

            @Test
            @DisplayName("Deve lançar exception caso não encontre a viagem atual para enviar a notificação para os alunos adeptos à desconexão.")
            void shouldThrowTravelNotFoundExceptionWhenTravelIsNotFoundDuringAutoDisconnect() {
                DistanceResponseDTO distanceResponseDTO = new DistanceResponseDTO(studentId, 350.0);
                Map<UUID, Long> redisMockData = Map.of(studentId, 1719876000000L);

                doReturn(List.of(distanceResponseDTO)).when(locationService).distanceBetweenPositions(travelId, liveLocationDTO);
                when(studentTravelRepository.findStudentsForAwayState(travelId)).thenReturn(List.of(studentAwayStateDTO));
                when(travelCacheService.getOrLoadTravelStaticCache(travelId)).thenReturn(travelCache);
                when(redisTrackingService.getStudentAwayTimestamp(travelId)).thenReturn(redisMockData);
                when(travelRepository.findById(travelId)).thenReturn(Optional.empty());

                assertThrows(EntityNotFoundException.class, () -> locationService.processStudentAwayState(studentAwayStateCheckEvent));

                verify(redisTrackingService, times(1)).getStudentAwayTimestamp(eq(travelId));

                verify(travelCacheService, times(1)).getOrLoadTravelStaticCache(eq(travelId));
                verify(studentTravelRepository, times(1)).findStudentsForAwayState(eq(travelId));
                verify(studentTravelRepository, times(1)).updateStudentTravelStatus(List.of(studentTravelEntity.getId()), StudentTravelStatus.AWAY_FROM_BUS);

                verify(studentTravelRepository, times(1)).disconnectedStudentFromTrip(any(), any(), any(), anyBoolean());
                verify(trackingNotificationService, never()).sendAutoDisconnectStudentNotification(any(), any());
            }
        }

    }

    @Nested
    class distanceBetweenPositions {
        UUID travelId;
        UUID studentId;

        Travel travelEntity;
        Student studentEntity;
        StudentTravel studentTravelEntity;
        LiveLocationDTO liveLocationDTO;
        StudentTrackingPositionDTO studentTrackingPositionDTO;

        @BeforeEach
        void setUp() {
            travelId = UUID.randomUUID();
            studentId = UUID.randomUUID();

            studentEntity = new Student(studentId, "student@gmail.com", "123456", "Maria", "Oliveira", "75988888888", "profile.jpg", GeneralStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), new Customer(), InstitutionType.UNIVERSITY, "Computer Science");

            GeoPosition position = new GeoPosition(
                    UUID.randomUUID(),
                    -23.55,
                    -46.63,
                    Instant.now(),
                    null
            );

            studentTravelEntity = new StudentTravel();
            studentTravelEntity.setId(UUID.randomUUID());
            studentTravelEntity.setStudent(studentEntity);
            studentTravelEntity.setPosition(position);
            studentTravelEntity.setEmbark(true);
            studentTravelEntity.setStudentTravelStatus(StudentTravelStatus.ACTIVE);

            travelEntity = new Travel();
            travelEntity.setId(travelId);
            travelEntity.setStudentTravels(new HashSet<>(Set.of(studentTravelEntity)));
            travelEntity.setTravelStatus(TravelStatus.TRAVELLING);

            liveLocationDTO = new LiveLocationDTO(-23.55, -46.63, null, null, null, null, Instant.now());

            studentTrackingPositionDTO = new StudentTrackingPositionDTO(studentId, -32.1223, -11.3233);
        }

        @Test
        @DisplayName("Deve realizar o cálculo e o mapeamento de distâncias de forma correta")
        void shouldCalculateAndMapDistancesCorrectly() {
            doReturn(Set.of(studentTrackingPositionDTO)).when(travelService).linkedStudentTravel(travelEntity.getId());

            when(routeCalculationService.calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(35.0);

            List<DistanceResponseDTO> result = locationService.distanceBetweenPositions(travelEntity.getId(), liveLocationDTO);

            assertNotNull(result);
            assertEquals(1, result.size());

            verify(travelService, times(1)).linkedStudentTravel(eq(travelEntity.getId()));
            verify(routeCalculationService, times(1)).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        }

        @Test
        @DisplayName("Deve realizar a filtragem de alunos sem posição registrada, retornando silenciosamente")
        void shouldSkipStudentsWithoutRegisteredLocation() {
            StudentTrackingPositionDTO studentTrackingWithoutPosition = new StudentTrackingPositionDTO(studentId, null, null);

            doReturn(Set.of(studentTrackingWithoutPosition)).when(travelService).linkedStudentTravel(travelEntity.getId());

            List<DistanceResponseDTO> result = locationService.distanceBetweenPositions(travelEntity.getId(), liveLocationDTO);

            assertNotNull(result); // deve retornar o studentId mas sem a distance no DTO

            verify(travelService, times(1)).linkedStudentTravel(eq(travelEntity.getId()));
            verify(routeCalculationService, never()).calculateHaversineDistanceInMeters(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        }
    }*/
}