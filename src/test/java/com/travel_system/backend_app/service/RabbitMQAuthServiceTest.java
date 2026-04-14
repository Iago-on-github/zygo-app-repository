package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RabbitMQAuthServiceTest {
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

    @InjectMocks
    private RabbitMQAuthService rabbitMQAuthService;

    @Mock
    private TokenConfig tokenConfig;
    @Mock
    private TravelService travelService;
    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rabbitMQAuthService, "rabbitmq_user", "backend_system_username");
        ReflectionTestUtils.setField(rabbitMQAuthService, "rabbitmq_password", "backend_system_password");
    }

    @Nested
    class authenticateMessaging {

        @Test
        @DisplayName("should authorize if it's the own system trying with success")
        void shouldAuthorizeIfItIsTheOwnSystemTryingWithSuccess() {
            String username = "backend_system_username";
            String password = "backend_system_password";

            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            assertTrue(result);
        }
    }
}