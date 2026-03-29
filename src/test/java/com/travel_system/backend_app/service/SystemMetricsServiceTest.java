package com.travel_system.backend_app.service;

import com.google.firebase.database.DatabaseException;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemMetricsServiceTest {

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

    @Mock
    private TravelRepository travelRepository;
    @Mock
    private RedisTrackingService redisTrackingService;
    @InjectMocks
    private SystemMetricsService systemMetricsService;

    @Nested
    class busAutoHealingMonitor {
        @Test
        @DisplayName("Should auto detect if bus is offline after eight minutes or more stopped")
        void shouldAutoDetectOfflineBusWithSuccess(){
            Travel travel = new Travel();

            UUID activeId = UUID.randomUUID();
            UUID expiredId = UUID.randomUUID();
            UUID noDataId = UUID.randomUUID();

            Set<String> uuids = Set.of(activeId.toString(), expiredId.toString(), noDataId.toString());

            long tenMinutes = System.currentTimeMillis() - 600000;
            long activeTimestamp = System.currentTimeMillis() - 60000;

            when(travelRepository.findById(expiredId)).thenReturn(Optional.of(travel));
            when(redisTrackingService.getAllActiveTravelsId()).thenReturn(uuids);

            when(redisTrackingService.getLastPingTimestamp(activeId)).thenReturn(activeTimestamp);
            when(redisTrackingService.getLastPingTimestamp(expiredId)).thenReturn(tenMinutes);
            when(redisTrackingService.getLastPingTimestamp(noDataId)).thenReturn(null);

            systemMetricsService.busAutoHealingMonitor();

            verify(travelRepository, times(1)).save(any());
            verify(redisTrackingService, times(1)).removeUnactiveTravel(expiredId);

            verify(redisTrackingService, never()).removeUnactiveTravel(activeId);
        }

        @Test
        @DisplayName("Method should continue even if LastPingTimestamp equals null")
        void shouldContinueEvenIfLastPingTimestampIsNull() {
            UUID activeId = UUID.randomUUID();
            UUID noDataId = UUID.randomUUID();
            UUID expiredId = UUID.randomUUID();
            Travel travel = new Travel();

            Set<String> uuids = Set.of(activeId.toString(), noDataId.toString(), expiredId.toString());

            long activeTimestamp = System.currentTimeMillis() - 60000;
            long expiredTimestamp = System.currentTimeMillis() - 600000;

            when(redisTrackingService.getAllActiveTravelsId()).thenReturn(uuids);
            when(travelRepository.findById(expiredId)).thenReturn(Optional.of(travel));
            when(travelRepository.save(any())).thenReturn(travel);

            when(redisTrackingService.getLastPingTimestamp(activeId)).thenReturn(activeTimestamp);
            when(redisTrackingService.getLastPingTimestamp(noDataId)).thenReturn(null);
            when(redisTrackingService.getLastPingTimestamp(expiredId)).thenReturn(expiredTimestamp);

            systemMetricsService.busAutoHealingMonitor();

            verify(travelRepository, times(1)).findById(expiredId);
            verify(redisTrackingService, times(1)).removeUnactiveTravel(expiredId);
            verify(redisTrackingService, times(1)).clearTravelLocationCache(expiredId);

            verify(redisTrackingService, never()).removeUnactiveTravel(activeId);
            verify(redisTrackingService, never()).removeUnactiveTravel(noDataId);
        }

        @Test
        @DisplayName("Throw exception when database fail")
        void throwExceptionWhenDatabaseError() {
            Travel travel = new Travel();
            travel.setTravelStatus(TravelStatus.FINISH);

            UUID activeId = UUID.randomUUID();
            UUID expiredId = UUID.randomUUID();
            UUID noDataId = UUID.randomUUID();

            Set<String> uuids = Set.of(activeId.toString(), expiredId.toString(), noDataId.toString());

            when(travelRepository.findById(any())).thenThrow(new RuntimeException("Data Connection Failed"));
            when(redisTrackingService.getAllActiveTravelsId()).thenReturn(uuids);

            assertThrows(RuntimeException.class, () -> systemMetricsService.busAutoHealingMonitor());

            verify(redisTrackingService, never()).removeUnactiveTravel(any());
            verify(redisTrackingService, never()).clearTravelLocationCache(any());
        }
    }

}