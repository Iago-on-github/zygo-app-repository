package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationServiceTest {
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
    private AsyncNotificationService asyncNotificationService;

    @Mock
    private RedisTrackingService redisTrackingService;
    @Mock
    private TravelRepository travelRepository;
    @Mock
    private TravelTrackingNotificationService trackingNotificationService;

    @Mock
    private StudentTravelRepository studentTravelRepository;

    private Travel travelEntity;
    private UUID travelId;
    private VelocityAnalysisDTO velocityAnalysisDTO;

    @BeforeEach
    void setUp() {
        travelId = UUID.randomUUID();
        travelEntity = new Travel();
        travelEntity.setId(travelId);

        velocityAnalysisDTO = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.NORMAL);
    }

    @Nested
    class processNotificationType {

        @Test
        @DisplayName("Deve retornar de forma silenciosa caso não seja requerida notificação")
        void shouldReturnImmediatelyWhenNotificationIsNotRequired() {
            asyncNotificationService.processNotificationType(travelId, velocityAnalysisDTO, ShouldNotify.SHOULD_NO_NOTIFY);

            verifyNoInteractions(travelRepository);
            verifyNoInteractions(redisTrackingService);
            verifyNoInteractions(trackingNotificationService);
        }

        @Test
        @DisplayName("Cenário 1.2: Deve direcionar para fluxo de ônibus lento quando ShouldNotify for SHOULD_NOTIFY_SLOW")
        void shouldRedirectToSlowNotificationWhenShouldNotifyIsSlow() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.SLOW);


            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW);

            // Verifica que o fluxo do SLOW foi executado por completo
            verify(travelRepository, times(1)).findById(travelId);
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);
            verify(trackingNotificationService, times(1))
                    .sendTrackingSlowMovementNotification(travelEntity, velocityAnalysis);

            verify(trackingNotificationService, never())
                    .sendTrackingStoppedMovementNotification(any(), any());
        }

        @Test
        @DisplayName("Cenário 1.3: Deve direcionar para fluxo de ônibus parado quando ShouldNotify for SHOULD_NOTIFY_STOPPED")
        void shouldRedirectToStoppedNotificationWhenShouldNotifyIsStopped() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.STOPPED);

            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED);

            // Verifica que o fluxo do STOPPED foi executado por completo
            verify(travelRepository, times(1)).findById(travelId);
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);
            verify(trackingNotificationService, times(1))
                    .sendTrackingStoppedMovementNotification(travelEntity, velocityAnalysis);

            verify(trackingNotificationService, never())
                    .sendTrackingSlowMovementNotification(any(), any());
        }

        @Test
        @DisplayName("Cenário 2.1: Deve enviar notificação de lentidão com sucesso e registrar no Redis")
        void shouldSendSlowNotificationWithSuccess() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.SLOW);


            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW);

            verify(travelRepository, times(1)).findById(travelId);
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);
            verify(trackingNotificationService, times(1))
                    .sendTrackingSlowMovementNotification(travelEntity, velocityAnalysis);
        }

        @Test
        @DisplayName("Cenário 2.2: Deve lançar EtaDataStatesInvalidException quando a análise de velocidade for nula")
        void shouldThrowExceptionWhenVelocityAnalysisIsNull() {
            assertThrows(EtaDataStatesInvalidException.class, () -> {
                asyncNotificationService.processNotificationType(travelId, null, ShouldNotify.SHOULD_NOTIFY_SLOW);
            });

            verifyNoInteractions(travelRepository, redisTrackingService, trackingNotificationService);
        }

        @Test
        @DisplayName("Cenário 2.4: Deve retornar silenciosamente sem enviar notificação quando o estado de movimento não for SLOW")
        void shouldReturnSilentlyWhenMovementStateIsNotSlow() {
            // Estado configurado como NORMAL ao invés de SLOW
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.NORMAL);

            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW);

            verify(travelRepository, times(1)).findById(travelId);
            // Garante que o registro no Redis e o envio no Firebase não foram chamados devido ao early return do estado incompatível
            verifyNoInteractions(redisTrackingService, trackingNotificationService);
        }

        @Test
        @DisplayName("Cenário 3.1: Deve enviar notificação de veículo parado com sucesso e registrar no Redis")
        void shouldSendStoppedNotificationWithSuccess() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.STOPPED);

            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED);

            verify(travelRepository, times(1)).findById(travelId);
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);
            verify(trackingNotificationService, times(1))
                    .sendTrackingStoppedMovementNotification(travelEntity, velocityAnalysis);
        }

        @Test
        @DisplayName("Cenário 3.2: Deve lançar EtaDataStatesInvalidException quando algum parâmetro obrigatório for nulo")
        void shouldThrowExceptionWhenRequiredParametersAreNull() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.STOPPED);

            // Testando com travelId nulo
            assertThrows(EtaDataStatesInvalidException.class, () -> asyncNotificationService.processNotificationType(null, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED));

            // Testando com velocityAnalysis nulo
            assertThrows(EtaDataStatesInvalidException.class, () -> asyncNotificationService.processNotificationType(travelId, null, ShouldNotify.SHOULD_NOTIFY_STOPPED));

            verifyNoInteractions(travelRepository, redisTrackingService, trackingNotificationService);
        }

        @Test
        @DisplayName("Cenário 3.3: Deve lançar EntityNotFoundException quando a viagem não for encontrada no banco de dados")
        void shouldThrowEntityNotFoundExceptionWhenTravelNotFound() {
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.STOPPED);

            when(travelRepository.findById(travelId)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED));

            verify(travelRepository, times(1)).findById(travelId);
            verifyNoInteractions(redisTrackingService, trackingNotificationService);
        }

        @Test
        @DisplayName("Cenário 3.4: Deve retornar silenciosamente sem enviar notificação quando o estado de movimento não for STOPPED")
        void shouldReturnSilentlyWhenMovementStateIsNotStopped() {
            // Estado configurado como NORMAL ao invés de STOPPED
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(85.5, 3600L, 85.5, 1718915200000.0, MovementState.NORMAL);

            when(travelRepository.findById(travelId)).thenReturn(Optional.of(travelEntity));

            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED);

            verify(travelRepository, times(1)).findById(travelId);
            // Garante que o registro no Redis e o envio no Firebase não foram chamados devido ao early return do estado incompatível
            verifyNoInteractions(redisTrackingService, trackingNotificationService);
        }
    }
}