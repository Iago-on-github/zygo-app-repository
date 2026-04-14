package com.travel_system.backend_app.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.enums.GeneralStatus;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

            verify(userRepository, never()).existsByEmailAndIdAndStatus(anyString(), any(), any());
            verifyNoInteractions(tokenConfig);
        }

        @Test
        @DisplayName("should return false if 'token' is invalid for the current user")
        void shouldReturnFalseIfTokenIsInvalidForTheUser() {
            // arrange
            String username = "backend_system_username";
            String password = "invalid_password";

            when(tokenConfig.validateToken(password)).thenReturn(false);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertFalse(result);

            verify(userRepository, never()).existsByEmailAndIdAndStatus(anyString(), any(), any());
            verifyNoMoreInteractions(tokenConfig);
        }

        @Test
        @DisplayName("should return false if user invalid")
        void shouldReturnFalseIfUserInvalid() {
            // arrange
            String username = UUID.randomUUID().toString();
            String password = "backend_system_password";

            when(tokenConfig.validateToken(password)).thenReturn(true);
            when(tokenConfig.getSubjectFromToken(password)).thenReturn("emailteste@gmail.com");

            when(userRepository.existsByEmailAndIdAndStatus(anyString(), any(), eq(GeneralStatus.ACTIVE)))
                    .thenReturn(false);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertFalse(result);

            verify(userRepository, times(1)).existsByEmailAndIdAndStatus(anyString(), any(), any());

            verify(tokenConfig, times(1)).validateToken(any());
            verify(tokenConfig, times(1)).getSubjectFromToken(any());
        }

        @Test
        @DisplayName("should auth login for valid user with success")
        void shouldAuthorizedLoginForValidUserWithSuccess() {
            // arrange
            String username = UUID.randomUUID().toString();
            String password = "backend_system_password";

            when(tokenConfig.validateToken(password)).thenReturn(true);
            when(tokenConfig.getSubjectFromToken(password)).thenReturn("emailteste@gmail.com");

            when(userRepository.existsByEmailAndIdAndStatus(anyString(), any(), eq(GeneralStatus.ACTIVE)))
                    .thenReturn(true);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertTrue(result);

            verify(userRepository, times(1)).existsByEmailAndIdAndStatus(anyString(), any(), any());

            verify(tokenConfig, times(1)).validateToken(any());
            verify(tokenConfig, times(1)).getSubjectFromToken(any());
        }

        @Test
        @DisplayName("should return false when error occurs in auth messaging process")
        void shouldReturnFalseWhenErrorOccursInAuthMessagingProcess() {
            // arrange
            String username = UUID.randomUUID().toString();
            String password = "invalid_password";

            when(tokenConfig.validateToken(password)).thenThrow(RuntimeException.class);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertFalse(result);
        }
    }
}