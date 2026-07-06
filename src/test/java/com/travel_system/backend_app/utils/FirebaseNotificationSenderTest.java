package com.travel_system.backend_app.utils;

import com.google.firebase.messaging.*;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.DeviceToken;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Platform;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.repository.DeviceTokenRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.engine.jdbc.batch.spi.Batch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.repository.query.Param;
import org.testcontainers.shaded.org.checkerframework.framework.qual.DefaultQualifierForUse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseNotificationSenderTest {

    @InjectMocks
    private FirebaseNotificationSender firebaseNotificationSender;
    
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRecipientResolver recipientResolver;

    @Nested
    class manageUserTokens {
        String userEmail = "useremail@teste.com";
        UserModel user;
        String token;
        DeviceToken deviceToken;

        @BeforeEach
        void setUp() {
            user = new UserModel(UUID.randomUUID(), userEmail, "123", "Teste", "Teste Dois", "7899999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

            token = "fcm_mock_token_12345";

            deviceToken = new DeviceToken(UUID.randomUUID(), user, token, Platform.WEB, true, NotificationAudience.ADMIN_ONLY);
        }

        @Test
        @DisplayName("Deve atualizar o token do usuário com sucesso quando ele já existe")
        void shouldUpdateUserTokenWithSuccessWhenTokenAlreadyExists() {
            Platform plat = Platform.IOS;

            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(user);
            when(deviceTokenRepository.findDeviceTokenByToken(token)).thenReturn(Optional.of(deviceToken));

            when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

            firebaseNotificationSender.manageUserToken(user.getEmail(), token, plat);

            verify(userRepository, times(1)).findUserByEmail(userEmail);
            verify(deviceTokenRepository, times(1)).findDeviceTokenByToken(token);

            assertEquals(user, deviceToken.getUser());
            assertEquals(plat, deviceToken.getPlatform());

            verify(deviceTokenRepository, times(1)).save(any(DeviceToken.class));
        }

        @Test
        @DisplayName("Deve registrar o token do usuário quando não existir")
        void shouldRegistryUserTokenWithSuccess() {
            String newUserToken = "12345_fcm";
            Platform platform = Platform.WEB;

            when(userRepository.findUserByEmail(userEmail)).thenReturn(user);
            when(deviceTokenRepository.findDeviceTokenByToken(newUserToken)).thenReturn(Optional.empty());

            when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

            ArgumentCaptor<DeviceToken> tokenArgumentCaptor = ArgumentCaptor.forClass(DeviceToken.class);

            firebaseNotificationSender.manageUserToken(user.getEmail(), newUserToken, platform);

            verify(userRepository, times(1)).findUserByEmail(userEmail);
            verify(deviceTokenRepository, times(1)).findDeviceTokenByToken(newUserToken);

            verify(deviceTokenRepository, times(1)).save(tokenArgumentCaptor.capture());
            DeviceToken savedDeviceToken = tokenArgumentCaptor.getValue();

            assertNotNull(savedDeviceToken);

            assertEquals(user, savedDeviceToken.getUser());
            assertEquals(newUserToken, savedDeviceToken.getToken());
            assertEquals(platform, savedDeviceToken.getPlatform());
        }

        @ParameterizedTest()
        @MethodSource("nullParametersProvider")
        void throwExceptionWhenParametersAreNull(String userEmail, String token, Platform platform) {
            assertThrows(DomainValidationException.class, () -> firebaseNotificationSender.manageUserToken(userEmail, token, platform));

            verify(userRepository, never()).findUserByEmail(anyString());
            verify(deviceTokenRepository, never()).findDeviceTokenByToken(anyString());
            verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
        }

        public static Stream<Arguments> nullParametersProvider() {
            return Stream.of(
                    Arguments.of(null, "fcm_mock_token_12345", Platform.WEB),
                    Arguments.of("exempleEmail@user.com", null, Platform.WEB),
                    Arguments.of("exempleEmail@user.com", "", Platform.WEB),
                    Arguments.of("exempleEmail@user.com", "fcm_mock_token_12345", null)
            );
        }

        @Test
        void throwExceptionWhenUserNotFound() {
            when(userRepository.findUserByEmail(user.getEmail())).thenReturn(null);

            assertThrows(EntityNotFoundException.class, () -> firebaseNotificationSender.manageUserToken(user.getEmail(), token, deviceToken.getPlatform()));

            verify(deviceTokenRepository, never()).findDeviceTokenByToken(anyString());
            verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
        }
    }

    @Nested
    class sendPushNotification {
        UserModel user;
        Set<String> validTokens = Set.of("fcm_token_001", "fcm_token_002", "fcm_token_003", "fcm_token_004");
        List<String> failureTokens = List.of("fcm_token_0010", "fcm_token_0011", "fcm_token_0012", "fcm_token_0013");
        PushNotificationCommandDTO commandDTO;

        @BeforeEach
        void setUp() {
            UUID travelId = UUID.randomUUID();

            user = new UserModel(UUID.randomUUID(), "userEmail@teste.com", "123", "Teste", "Teste Dois", "7899999999", null, GeneralStatus.ACTIVE, LocalDateTime.now(), null, new Customer());

            Map<String, String> data = Map.of(
                    "eventType", "AUTO_DISCONNECTED_STUDENT",
                    "travelId", travelId.toString(),
                    "studentStatus", "AUTO_DISCONNECTED"
            );

            commandDTO = new PushNotificationCommandDTO(NotificationAudience.SPECIFIC_USER, user.getId(), UUID.randomUUID(), travelId, "title_exemple", "notification_msg", "linkTeste", Priority.NORMAL, data);
        }

        @Test
        @DisplayName("Verifica o envio bem sucedido para todos os dispositivos")
        void shouldSendTokenForAllDevices() throws FirebaseMessagingException {
            // mocks SDK firebase
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            BatchResponse batchResponse = mock(BatchResponse.class);

            // resolveTokensByAudience = specificUser
            when(recipientResolver.resolveSpecificUser(user.getId())).thenReturn(validTokens);

            when(batchResponse.getSuccessCount()).thenReturn(4);
            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            try (MockedStatic<FirebaseMessaging> firebaseMessagingMockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingMockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

                firebaseNotificationSender.sendPushNotification(commandDTO);

                // valida se o SKD foi acionado exatamente uma vez
                verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));

                verify(batchResponse, times(1)).getSuccessCount();
                verify(batchResponse, times(1)).getFailureCount();

                // devicetokens = 0 então NUNCA deve tentar desativar os tokens
                verify(deviceTokenRepository, never()).deactivateTokensByValue(anyList());
            }
        }

        @Test
        @DisplayName("Valida que o firebase enviou a mensagem, mas coleta tokens inválidos")
        void shouldDeactivateInvalidTokensWhenFirebaseReturnsPartialFailures() throws FirebaseMessagingException {
            // SDK do Firebase
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            BatchResponse batchResponse = mock(BatchResponse.class);

            List<String> tokensList = validTokens.stream().toList();
            when(recipientResolver.resolveSpecificUser(commandDTO.userId())).thenReturn(validTokens);

            List<SendResponse> mockResponses = new ArrayList<>();

            // sucessos
            SendResponse successResponse = mock(SendResponse.class);
            when(successResponse.isSuccessful()).thenReturn(true);
            mockResponses.add(successResponse);
            mockResponses.add(successResponse);

            // UNREGISTERED (Tokens inválidos/expirados)
            SendResponse failureResponse = mock(SendResponse.class);
            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);

            when(failureResponse.isSuccessful()).thenReturn(false);
            when(failureResponse.getException()).thenReturn(mockException);
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

            mockResponses.add(failureResponse); // Associado ao fcm_token_003
            mockResponses.add(failureResponse); // Associado ao fcm_token_004

            // contadores do BatchResponse
            when(batchResponse.getSuccessCount()).thenReturn(2);
            when(batchResponse.getFailureCount()).thenReturn(2);
            when(batchResponse.getResponses()).thenReturn(mockResponses);

            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass(List.class);

            try (MockedStatic<FirebaseMessaging> firebaseMessagingMockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingMockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

                firebaseNotificationSender.sendPushNotification(commandDTO);

                verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));
                verify(batchResponse, times(1)).getSuccessCount();
                verify(batchResponse, times(2)).getFailureCount();

                verify(deviceTokenRepository, times(1)).deactivateTokensByValue(listCaptor.capture());

                // Valida se os tokens capturados são exatamente os dois últimos que falharam
                List<String> capturedTokens = listCaptor.getValue();
                assertEquals(2, capturedTokens.size(), "Deveria ter coletado exatamente 2 tokens inválidos");
                assertTrue(capturedTokens.contains(tokensList.get(2))); // fcm_token_003
                assertTrue(capturedTokens.contains(tokensList.get(3))); // fcm_token_004
            }
        }

        @Test
        @DisplayName("Deve retornar de forma silenciosa e subir logging quando os tokens da audience for null")
        void shouldReturnSilentlyWhenTokensByAudienceIsNull() throws FirebaseMessagingException {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            BatchResponse batchResponse = mock(BatchResponse.class);

            // resolveTokensByAudience = specificUser
            when(recipientResolver.resolveSpecificUser(user.getId())).thenReturn(null);

            firebaseNotificationSender.sendPushNotification(commandDTO);

            verify(firebaseMessaging, never()).sendEachForMulticast(any());
            verify(deviceTokenRepository, never()).deactivateTokensByValue(any());
        }

        @Test
        @DisplayName("Deve capturar FirebaseMessagingException global e tratar internamente sem propagar a exceção")
        void shouldHandleFirebaseMessagingExceptionWithoutPropagatingIt() throws FirebaseMessagingException {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

            when(recipientResolver.resolveSpecificUser(commandDTO.userId())).thenReturn(validTokens);

            // exception real do SDK do Firebase para simular a falha global
            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);

            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenThrow(mockException);

            // interceptar o FirebaseMessaging.getInstance()
            try (MockedStatic<FirebaseMessaging> firebaseMessagingMockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingMockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

                assertDoesNotThrow(() -> firebaseNotificationSender.sendPushNotification(commandDTO));

                verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));

                verify(deviceTokenRepository, never()).deactivateTokensByValue(anyList());
            }
        }

        @Test
        @DisplayName("Deve registrar erro no log mas não desativar tokens quando houver falhas no Firebase que não sejam por tokens inválidos")
        void shouldLogFailureButNotDeactivateTokensWhenFailuresAreNotDueToInvalidTokens() throws FirebaseMessagingException {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            BatchResponse batchResponse = mock(BatchResponse.class);

            when(recipientResolver.resolveSpecificUser(commandDTO.userId())).thenReturn(validTokens);

            List<SendResponse> mockResponses = new ArrayList<>();

            // Firebase devolveu erro de QUOTA_EXCEEDED para as mensagens
            SendResponse failureResponse = mock(SendResponse.class);
            FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);

            when(failureResponse.isSuccessful()).thenReturn(false);
            when(failureResponse.getException()).thenReturn(mockException);
            // QUOTA_EXCEEDED indica que o servidor barrou o envio por limite, não que o token do usuário morreu
            when(mockException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.QUOTA_EXCEEDED);

            for (int i = 0; i < validTokens.size(); i++) {
                mockResponses.add(failureResponse);
            }

            when(batchResponse.getSuccessCount()).thenReturn(0);
            when(batchResponse.getFailureCount()).thenReturn(validTokens.size());
            when(batchResponse.getResponses()).thenReturn(mockResponses);

            when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            try (MockedStatic<FirebaseMessaging> firebaseMessagingMockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingMockedStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

                assertDoesNotThrow(() -> firebaseNotificationSender.sendPushNotification(commandDTO));

                verify(firebaseMessaging, times(1)).sendEachForMulticast(any(MulticastMessage.class));
                verify(batchResponse, times(1)).getSuccessCount();
                verify(batchResponse, times(2)).getFailureCount(); // Uma vez no log de info, outra no if de erro
                verify(batchResponse, times(1)).getResponses();

                verify(deviceTokenRepository, never()).deactivateTokensByValue(anyList());
            }
        }
    }
}