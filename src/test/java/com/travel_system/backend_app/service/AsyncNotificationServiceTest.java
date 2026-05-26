package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private FirebaseNotificationSender firebaseNotificationSender;

    @Mock
    private StudentTravelRepository studentTravelRepository;

    @Nested
    class processNotificationType {

        @Test
        @DisplayName("should process notification and no notify with success")
        void shouldProcessNotificationAndNoNotifyWithSuccess() {
            // arrange
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.NORMAL);

            // act
            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NO_NOTIFY, traceId);

            // assert
            verify(redisTrackingService, never()).markNotificationAsSent(travelId);

            verify(studentTravelRepository, never()).findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId);

            verify(firebaseNotificationSender, never())
                    .pushNotificationToFirebase(argThat(event -> event.studentId().equals(studentId) &&
                            event.travelId().equals(travelId) &&
                            event.movementState().equals(MovementState.SLOW) &&
                            event.traceId().equals(traceId)));
        }

        @Test
        @DisplayName("should process notification and returns slow notification with success")
        void shouldProcessNotificationAndReturnsSlowNotificationWithSuccess() {
            // arrange
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.SLOW);

            List<UUID> studentsId = List.of(studentId);

            when(studentTravelRepository.findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId)).thenReturn(studentsId);

            // act
            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW, traceId);

            // assert
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);

            verify(studentTravelRepository, times(1)).findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId);

            verify(firebaseNotificationSender, times(1))
                    .pushNotificationToFirebase(argThat(event -> event.studentId().equals(studentId) &&
                            event.travelId().equals(travelId) &&
                            event.movementState().equals(MovementState.SLOW) &&
                            event.traceId().equals(traceId)));

        }

        @Test
        @DisplayName("should process notification and returns stopped notification with success")
        void shouldProcessNotificationAndReturnsStoppedNotificationWithSuccess() {
            // arrange
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.STOPPED);

            List<UUID> studentsId = List.of(studentId);

            when(studentTravelRepository.findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId)).thenReturn(studentsId);

            // act
            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_STOPPED, traceId);

            // assert
            verify(redisTrackingService, times(1)).markNotificationAsSent(travelId);

            verify(studentTravelRepository, times(1)).findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId);

            verify(firebaseNotificationSender, times(1))
                    .pushNotificationToFirebase(argThat(event -> event.studentId().equals(studentId) &&
                            event.travelId().equals(travelId) &&
                            event.movementState().equals(MovementState.STOPPED) &&
                            event.traceId().equals(traceId)));
        }

        @Test
        @DisplayName("throw exception when travelId is null")
        void throwExceptionWhenTravelIdIsNull() {
            // arrange
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.STOPPED);
            UUID traceId = UUID.randomUUID();

            // act & assert
            assertThrows(EtaDataStatesInvalidException.class, () -> {
               asyncNotificationService.processNotificationType(null, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW, traceId);
            });

            verifyNoInteractions(redisTrackingService, studentTravelRepository, firebaseNotificationSender);
        }

        @Test
        @DisplayName("throw exception when velocity analysis dto is null")
        void throwExceptionWhenVelocityAnalysisIsNull() {
            // arrange
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.STOPPED);
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();

            // act & assert
            assertThrows(EtaDataStatesInvalidException.class, () -> {
                asyncNotificationService.processNotificationType(travelId, null, ShouldNotify.SHOULD_NOTIFY_SLOW, traceId);
            });

            verifyNoInteractions(redisTrackingService, studentTravelRepository, firebaseNotificationSender);
        }

        @Test
        @DisplayName("should returns if movement state are inconsistent with should notify")
        void shouldReturnsIfMovementStateAreInconsistentWithShouldNotify() {
            // arrange
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.STOPPED);
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();

            // act
            asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW, traceId);

            // assert
            verifyNoInteractions(redisTrackingService, studentTravelRepository, firebaseNotificationSender);
        }

        @Test
        @DisplayName("it should throw an exception when Firebase crashes and not interrupt any other services.")
        void shouldThrowAnExceptionWhenFirebaseFailsAndNotInterruptAnyOtherElseSenders() {
            // arrange
            VelocityAnalysisDTO velocityAnalysis = new VelocityAnalysisDTO(3.2, 4L, 8.0, 2.1, MovementState.SLOW);
            UUID traceId = UUID.randomUUID();
            UUID travelId = UUID.randomUUID();

            UUID studentIdThatFails = UUID.randomUUID();
            UUID studentIdThatSucceeds = UUID.randomUUID();

            List<UUID> studentList = List.of(studentIdThatFails, studentIdThatSucceeds);

            when(studentTravelRepository.findStudentIdsByTravelIdAndDisembarkHourIsNull(travelId)).thenReturn(studentList);

            // throw exception to first student
            doThrow(new RuntimeException("Firebase unavailable"))
                    .when(firebaseNotificationSender)
                    .pushNotificationToFirebase(argThat(event -> event.studentId().equals(studentIdThatFails)));

            // assert
            assertDoesNotThrow(() -> {
                asyncNotificationService.processNotificationType(travelId, velocityAnalysis, ShouldNotify.SHOULD_NOTIFY_SLOW, traceId);
            });

            verify(firebaseNotificationSender, times(2)).pushNotificationToFirebase(any());
        }
    }
}