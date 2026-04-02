package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.TravelReports;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelReportsRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private TravelReportsRepository travelReportsRepository;
    @Mock
    private TravelRepository travelRepository;

    @InjectMocks
    private TravelService travelService;

    private final ArgumentCaptor<TravelReports> travelReportsCaptor = ArgumentCaptor.forClass(TravelReports.class);

    @Nested
    class endTravel {
        @DisplayName("should generate metrics to Travel Reports with success")
        @Test
        void shouldGenerateFullTravelReportWithSuccess() {
            Travel travel = new Travel();

            travel.setId(UUID.randomUUID());
            travel.setTravelStatus(TravelStatus.TRAVELLING);
            travel.setStartHourTravel(Instant.now().minusSeconds(180));

            Set<StudentTravel> studentTravels = Set.of(
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now(), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, Instant.now().minusSeconds(200), null, null),
                    new StudentTravel(UUID.randomUUID(), travel, null, true, null, null, null)
            );

            travel.setStudentTravels(studentTravels);

            when(travelRepository.findById(travel.getId())).thenReturn(Optional.of(travel));
            when(redisTrackingService.getAccumulatedDistance(travel.getId())).thenReturn(String.valueOf(1500.0));

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
    }
}