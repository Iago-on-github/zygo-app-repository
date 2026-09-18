package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.CsvSources;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisNotificationServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

    private RedisNotificationService redisNotificationService;

    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private HashOperations hashOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        redisNotificationService = new RedisNotificationService(redisTemplate);
    }

    @Nested
    class readNotificationState {

        @Test
        @DisplayName("should read and returns notificationStateDTO with success")
        void shouldReadNotificationStateWithSuccess() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            String key = "notification:" + travelId + ":" + studentId;

            Map<String, String> mockData = new HashMap<>();
            mockData.put("zone", "FAR");
            mockData.put("lastDistanceNotified", "300.0");
            mockData.put("lastNotificationAt", "lastNotify");
            mockData.put("timeStamp", "now");

            when(hashOperations.entries(key)).thenReturn(mockData);

            NotificationStateDTO result = redisNotificationService.readNotificationState(travelId, studentId);

            assertNotNull(result);

            assertEquals("FAR", result.zone());
            assertEquals("lastNotify", result.lastNotificationAt());

            verify(hashOperations, times(1)).entries(any());
        }

        @ParameterizedTest
        @DisplayName("should return silently when entry data are null")
        @MethodSource("nullFieldsProvider")
        void shouldReturnSilentlyWhenEntryDataAreNull(UUID travelId, UUID studentId) {
            NotificationStateDTO result = redisNotificationService.readNotificationState(travelId, studentId);

            assertNull(result);

            verifyNoInteractions(hashOperations);
        }

        public static Stream<Arguments> nullFieldsProvider() {
            return Stream.of(
                    Arguments.of(UUID.randomUUID(), null),
                    Arguments.of(null, UUID.randomUUID())
            );
        }

        @Test
        @DisplayName("should return silently when data not yet available for reading")
        void shouldReturnSilentlyWhenDataNotYetAvailableForReading() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            String key = "notification:" + travelId + ":" + studentId;

            when(hashOperations.entries(key)).thenReturn(Map.of());

            NotificationStateDTO result = redisNotificationService.readNotificationState(travelId, studentId);

            assertNull(result);

            verify(hashOperations, times(1)).entries(any());
        }

    }

    @Nested
    class verifyNotificationState {

        @Test
        @DisplayName("Should send notification when elapsed time exceeds configured notification threshold")
        void shouldSendNotificationWhenElapsedTimeExceedsTimeToNotifyWithSuccess() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            Double currentDistance = 1200.0;

            Instant lastNotifyAt = Instant.now().minusSeconds(730000); // mais que 12 min

            NotificationStateDTO notificationStateDTO = new NotificationStateDTO(
                    "FAR",
                    String.valueOf(currentDistance),
                    String.valueOf(lastNotifyAt.toEpochMilli()),
                    Instant.now().toString());

            Boolean result   = redisNotificationService.verifyNotificationState(travelId, studentId, currentDistance, notificationStateDTO);

            assertTrue(result, "deve notificar respeitando o intervalo de 12 minutos interno");
        }

        @Test
        @DisplayName("Should send notification when does not have last notification reliable")
        void shouldSendNotificationWhenDoesNotHaveLastNotificationReliable() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            Double currentDistance = 1200.0;

            NotificationStateDTO notificationStateDTO = new NotificationStateDTO(
                    "FAR",
                    String.valueOf(currentDistance),
                    null, // sem última notificação confiável
                    Instant.now().toString());

            Boolean result   = redisNotificationService.verifyNotificationState(travelId, studentId, currentDistance, notificationStateDTO);

            assertTrue(result, "deve notificar pois não ha última notificação confiável");
        }

        @Test
        @DisplayName("Should send notification when current zone differs from stored zone")
        void shouldSendNotificationWhenCurrentZoneDiffersFromStoredZone() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            Double currentDistance = 100.0;

            Instant lastNotifyAt = Instant.now().minusSeconds(730000); // mais que 12 min

            NotificationStateDTO notificationStateDTO = new NotificationStateDTO(
                    "FAR",
                    String.valueOf(currentDistance),
                    String.valueOf(lastNotifyAt.toEpochMilli()),
                    Instant.now().toString());

            Boolean result = redisNotificationService.verifyNotificationState(travelId, studentId, currentDistance, notificationStateDTO);

            assertTrue(result, "deve notificar pois a zona atual é diferente da zona armazenada");
        }

        @Test
        @DisplayName("Should send notification when distance delta is greater than or equal to step")
        void shouldSendNotificationWhenDistanceDeltaIsGreaterThanOrEqualToStep() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            Double currentDistance = 10.0;

            Instant lastNotifyAt = Instant.now().minusSeconds(730000); // mais que 12 min

            NotificationStateDTO notificationStateDTO = new NotificationStateDTO(
                    "NEAR",
                    String.valueOf(currentDistance),
                    String.valueOf(lastNotifyAt.toEpochMilli()),
                    Instant.now().toString());

            Boolean result   = redisNotificationService.verifyNotificationState(travelId, studentId, 50.0, notificationStateDTO);

            assertTrue(result, "deve notificar pois a distancia delta é maior que o step atual");
        }

        @Test
        @DisplayName("should send notification when stored state is empty")
        void shouldSendNotificationWhenStoredStateIsEmpty() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            Double currentDistance = 100.0;

            Boolean result   = redisNotificationService.verifyNotificationState(travelId, studentId, currentDistance, null);

            assertTrue(result, "deve notificar pois o dto de entrada ou a propriedade 'zone' é nulo/a");
        }
    }

    @Nested
    class updateNotificationState {

        @Test
        @DisplayName("should create initial state when current state is empty")
        void shouldCreateInitialStateWhenCurrentStateIsEmpty() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();
            String key = "notification:" + travelId + ":" + studentId;

            Instant lastNotifyAt = Instant.now().minusSeconds(730000); // mais que 12 min

            NotificationStateDTO stateDTO = new NotificationStateDTO(
                    "NEAR",
                    String.valueOf(200.0),
                    String.valueOf(lastNotifyAt.toEpochMilli()),
                    String.valueOf(Instant.now()));

            when(hashOperations.entries(key)).thenReturn(Map.of());

            redisNotificationService.updateNotificationState(travelId, studentId, stateDTO);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(key), mapCaptor.capture());
            Map<String, String> storageValues = mapCaptor.getValue();

            assertEquals(stateDTO.zone(), storageValues.get("zone"));
            assertEquals(stateDTO.lastDistanceNotified(), storageValues.get("lastDistanceNotified"));

            assertNotNull(storageValues.get("timeStamp"));
        }

        @Test
        @DisplayName("should include only the changed field in the update map")
        void shouldIncludeOnlyChangedField() {
            Map<String, String> current = new HashMap<>(Map.of(
                    "zone", "NEAR",
                    "lastDistanceNotified", "500.0",
                    "lastNotificationAt", "12345"
            ));
            when(hashOperations.entries(anyString())).thenReturn(current);

            NotificationStateDTO newState = new NotificationStateDTO("FAR",
                    "500.0",
                    "12345",
                    null);

            redisNotificationService.updateNotificationState(UUID.randomUUID(), UUID.randomUUID(), newState);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOperations).putAll(anyString(), captor.capture());

            Map<String, String> sentToRedis = captor.getValue();

            assertTrue(sentToRedis.containsKey("zone"));
            assertTrue(sentToRedis.containsKey("timeStamp"));
            assertFalse(sentToRedis.containsKey("lastDistanceNotified"));
        }

        @Test
        @DisplayName("Should update state and timestamp when fields to update are not empty")
        void shouldUpdateStateWhenFieldsToUpdateIsNotEmpty() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            String key = "notification:" + travelId + ":" + studentId;

            NotificationStateDTO newState = new NotificationStateDTO("FAR",
                    "500.0",
                    "12345",
                    null);

            Map<String, String> current = new HashMap<>(Map.of(
                    "zone", "NEAR",
                    "lastDistanceNotified", "500.0",
                    "lastNotificationAt", "12345"
            ));

            when(hashOperations.entries(key)).thenReturn(current);

            redisNotificationService.updateNotificationState(travelId, studentId, newState);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            verify(hashOperations, times(1)).putAll(eq(key), mapCaptor.capture());
            Map<String, String> storageValue = mapCaptor.getValue();

            assertEquals("FAR", storageValue.get("zone"));
            assertNotNull(storageValue.get("timeStamp"), "O timestamp deve ter sido gerado internamente");
            assertFalse(storageValue.containsKey("lastDistanceNotified"));
        }

        @Test
        @DisplayName("should not update when state is identical")
        void shouldNotUpdateWhenStateIsIdentical() {
            UUID travelId = UUID.randomUUID();
            UUID studentId = UUID.randomUUID();

            NotificationStateDTO state = new NotificationStateDTO("NEAR", "500.0", "12345", null);
            Map<String, String> current = Map.of(
                    "zone", "NEAR",
                    "lastDistanceNotified", "500.0",
                    "lastNotificationAt", "12345"
            );

            when(hashOperations.entries(anyString())).thenReturn(current);

            redisNotificationService.updateNotificationState(travelId, studentId, state);

            verify(hashOperations, never()).putAll(anyString(), anyMap());
        }
    }
}