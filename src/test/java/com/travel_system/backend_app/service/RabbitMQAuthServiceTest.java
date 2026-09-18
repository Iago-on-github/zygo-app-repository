package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rabbitMQAuthService, "rabbitmq_user", "backend_system_username");
        ReflectionTestUtils.setField(rabbitMQAuthService, "rabbitmq_password", "backend_system_password");
    }

/*    @Nested
    class authenticateMessaging {

        @Test
        @DisplayName("should authorize if it's the own system trying with success")
        void shouldAuthorizeIfItIsTheOwnSystemTryingWithSuccess() {
            String username = "backend_system_username";
            String password = "backend_system_password";

            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            assertTrue(result);

            verify(userAccountRepository, never()).existsByEmailAndIdAndStatus(anyString(), any(), any());
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

            verify(userAccountRepository, never()).existsByEmailAndIdAndStatus(anyString(), any(), any());
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

            when(userAccountRepository.existsByEmailAndIdAndStatus(anyString(), any(), eq(GeneralStatus.ACTIVE)))
                    .thenReturn(false);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertFalse(result);

            verify(userAccountRepository, times(1)).existsByEmailAndIdAndStatus(anyString(), any(), any());

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

            when(userAccountRepository.existsByEmailAndIdAndStatus(anyString(), any(), eq(GeneralStatus.ACTIVE)))
                    .thenReturn(true);

            // act
            boolean result = rabbitMQAuthService.authenticateMessaging(username, password);

            // asserts
            assertTrue(result);

            verify(userAccountRepository, times(1)).existsByEmailAndIdAndStatus(anyString(), any(), any());

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
    }*/

    @Nested
    class authenticateVHost {

        @Test
        @DisplayName("should allow access to the vHost from user and ip with success ")
        void shouldAllowAccessToTheVHostWithSuccess() {
            String usernameId = UUID.randomUUID().toString();
            String vHost = "/";
            String ip = "192.383.221.93";

            boolean result = rabbitMQAuthService.authenticateVHost(usernameId, vHost, ip);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false and not allow access to the vHost")
        void shouldReturnFalseAndNotAllowAccessToTheVHost() {
            String usernameId = UUID.randomUUID().toString();
            String vHost = "invalid_vHost";
            String ip = "192.383.221.93";

            boolean result = rabbitMQAuthService.authenticateVHost(usernameId, vHost, ip);

            assertFalse(result);
        }
    }

    @Nested
    class authenticateResource {

        @Test
        @DisplayName("should never allow permission for create or delete server structures")
        void shouldNeverAllowPermissionForModifyServerStructures() {
            String username = "username";
            String resource = "resource";
            String permission = "configure";
            String vhost = "/";
            String name = "name";

            boolean result = rabbitMQAuthService.authenticateResource(username, vhost, resource, name, permission);

            assertFalse(result);
        }

        @ParameterizedTest
        @DisplayName("should allow read and/or write in public exchanges with success")
        @CsvSource({
                "user_test, /, topic, topic_name, read, true",    // valid
                "user_test, /, topic, topic_name, write, true",   // valid
                "user_test, /, queue_name, write, queue, false",  // invalid
                "user_test, /, ex_name, read, exchange, false",   // invalid
                "user_test, /, any_name, configure, topic, false" // invalid
        })
        void shouldAllowReadAndWriteInPublicExchanges(String username, String vhost, String resource, String name, String permission, boolean expectedResult) {
            boolean result = rabbitMQAuthService.authenticateResource(username, vhost, resource, name, permission);

            assertEquals(expectedResult, result);
        }

        @ParameterizedTest
        @DisplayName("")
        @CsvSource({
                "ex1_invalid, /, invalid_topic, ex1_topic_name, not_read, false",    // invalid
                "ex2_invalid, /, invalid_topic, ex2_topic_name, not_write, false",   // invalid
                "ex3_invalid, /, queue_name, not_write, queue, false",  // invalid
                "ex4_invalid, /, ex_name, not_read, exchange, false",   // invalid
        })
        void shouldReturnFalseWhenThePermissionNotAllowed(String username, String vhost, String resource, String name, String permission, boolean expectedResult) {
            boolean result = rabbitMQAuthService.authenticateResource(username, vhost, resource, name, permission);

            assertEquals(expectedResult, result);
        }

    }

    @Nested
    class authenticateTopic {

        @Test
        @DisplayName("should return driver logged if permission equals 'publish' ")
        void shouldReturnDriverLoggedIfPermissionEqualsPublish() {
            String username = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();


            String routingKey = "v1.gps.city." + travelId;
            String permission = "publish";

            when(travelService.isDriverLogged(eq(username), eq(travelId))).thenReturn(true);

            boolean result = rabbitMQAuthService.authenticateTopic(username, routingKey, permission);

            assertTrue(result);

            verify(travelService, times(1)).isDriverLogged(anyString(), any());

            verify(travelService, never()).isStudentLogged(any(), any());
        }

        @Test
        @DisplayName("should return student logged if permission equals 'subscribe' ")
        void shouldReturnStudentLoggedIfPermissionEqualsSubscribe() {
            String username = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();

            UUID studentId = UUID.fromString(username);

            String routingKey = "v1.gps.city." + travelId;
            String permission = "subscribe";

            when(travelService.isStudentLogged(eq(studentId), eq(travelId))).thenReturn(true);

            boolean result = rabbitMQAuthService.authenticateTopic(username, routingKey, permission);

            assertTrue(result);

            verify(travelService, times(1)).isStudentLogged(any(), any());

            verify(travelService, never()).isDriverLogged(any(), any());
        }

        @Test
        @DisplayName("should return false when error occurs in topic auth for the user")
        void shouldReturnFalseWhenErrorOccursInTopicAuthForTheUser() {
            String username = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();

            UUID studentId = UUID.fromString(username);

            String routingKey = "v1.gps.city." + travelId;
            String permission = "subscribe";

            when(travelService.isStudentLogged(eq(studentId), eq(travelId))).thenThrow(RuntimeException.class);

            boolean result = rabbitMQAuthService.authenticateTopic(username, routingKey, permission);

            assertFalse(result);

            verify(travelService, times(1)).isStudentLogged(any(), any());

            verify(travelService, never()).isDriverLogged(any(), any());
        }

        @Test
        @DisplayName("should return false when permissions non matching")
        void shouldReturnFalseWhenPermissionsNonMatching() {
            String username = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();

            String routingKey = "v1.gps.city." + travelId;
            String permission = "permission-non-matching";

            boolean result = rabbitMQAuthService.authenticateTopic(username, routingKey, permission);

            assertFalse(result);

            verify(travelService, never()).isStudentLogged(any(), any());

            verify(travelService, never()).isDriverLogged(any(), any());
        }
    }
}