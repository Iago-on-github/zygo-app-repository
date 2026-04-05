package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.Optional;
import java.util.UUID;
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

}