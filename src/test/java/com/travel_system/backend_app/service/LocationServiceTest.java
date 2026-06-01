package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.TravelException;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentRepository;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     */

    @InjectMocks
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
    private StudentRepository studentRepository;

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

    @Nested
    class processStudentAwayState {
        UUID travelId;
        Travel travelEntity;
        Student studentEntity;
        StudentTravel studentTravelEntity;
        LiveLocationDTO liveLocationDTO;
        DistanceResponseDTO distanceResponse;
        StudentTravel studentTravel;

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
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(null);

                locationService.processStudentAwayState(travelId, liveLocationDTO);

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
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));

                // timestamp recente, tempo ainda não esgotado
                long recentTimestamp = Instant.now().toEpochMilli();
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(recentTimestamp);

                locationService.processStudentAwayState(travelId, liveLocationDTO);

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

                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponse));

                when(studentRepository.findByEmail(any())).thenReturn(Optional.of(studentEntity));
                when(studentTravelRepository.findByTravelIdAndStudentId(any(), any())).thenReturn(Optional.of(studentTravel));

                long millis = TimeUnit.MINUTES.toMillis(7);
                when(redisTrackingService.getStudentAwayTimestamp(travelId, distanceResponse)).thenReturn(millis);

                locationService.processStudentAwayState(travelId, liveLocationDTO);

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
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO)).thenReturn(List.of(distanceResponseDTO));

                locationService.processStudentAwayState(travelEntity.getId(), liveLocationDTO);

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

                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse, distanceResponse2));

                when(redisTrackingService.getStudentAwayTimestamp(eq(travelId), any())).thenReturn(null);

                locationService.processStudentAwayState(travelId, liveLocationDTO);

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
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                assertThrows(EntityNotFoundException.class, () -> locationService.processStudentAwayState(travelId, liveLocationDTO));

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            void throwExceptionWhenTravelIsNotTravelling() {
                travelEntity.setTravelStatus(TravelStatus.PENDING);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                assertThrows(TravelException.class, () -> locationService.processStudentAwayState(travelId, liveLocationDTO));

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            @DisplayName("should log warning and ignore student when no matching StudentTravel is found")
            void shouldIgnoreStudentWhenNoMatchingStudentTravelIsFound() {
                travelEntity.setStudentTravels(null);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                locationService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            void shouldIgnoreStudentWhenIsNotEmbark() {
                studentTravelEntity.setEmbark(false);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                locationService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @ParameterizedTest
            @DisplayName("should ignore student when your status equals AUTO_DISCONNECTED or LEFT")
            @MethodSource("studentTravelStatusProvider")
            void shouldIgnoreStudentWhenYourStatusIsInvalidForAlgorithm(StudentTravelStatus studentTravelStatus) {
                studentTravelEntity.setStudentTravelStatus(studentTravelStatus);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                locationService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            public static Stream<Arguments> studentTravelStatusProvider() {
                return Stream.of(
                        Arguments.of(StudentTravelStatus.AUTO_DISCONNECTED),
                        Arguments.of(StudentTravelStatus.LEFT   )
                );
            }

            @Test
            void shouldIgnoreStudentWhenYourPositionIsNull() {
                studentTravelEntity.setPosition(null);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                locationService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }

            @Test
            void shouldIgnoreStudentWhenStudentIsNull() {
                studentTravelEntity.setStudent(null);

                when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));
                when(locationService.distanceBetweenPositions(travelId, liveLocationDTO))
                        .thenReturn(List.of(distanceResponse));

                locationService.processStudentAwayState(travelId, liveLocationDTO);

                verifyNoInteractions(redisTrackingService);
                verifyNoInteractions(studentTravelRepository);

                verifyNoMoreInteractions(travelRepository);
            }
        }

    }

}