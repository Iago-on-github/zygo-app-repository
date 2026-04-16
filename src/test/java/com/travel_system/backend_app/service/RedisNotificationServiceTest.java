package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.CsvSources;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

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


    }
}