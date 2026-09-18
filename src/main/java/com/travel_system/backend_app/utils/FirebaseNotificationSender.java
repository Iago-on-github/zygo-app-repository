package com.travel_system.backend_app.utils;

import com.google.firebase.messaging.*;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.model.PushNotificationDeviceToken;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.notifications.PushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.NotificationAudience;
import com.travel_system.backend_app.model.enums.Platform;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FirebaseNotificationSender {

    private final PushNotificationDeviceTokenRepository deviceTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationRecipientResolver notificationRecipientResolver;
    private final FirebaseMessaging firebaseMessaging;

    private static final Logger logger = LoggerFactory.getLogger(FirebaseNotificationSender.class);

    public FirebaseNotificationSender(PushNotificationDeviceTokenRepository deviceTokenRepository, UserAccountRepository userAccountRepository, NotificationRecipientResolver notificationRecipientResolver, FirebaseMessaging firebaseMessaging) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationRecipientResolver = notificationRecipientResolver;
        this.firebaseMessaging = firebaseMessaging;
    }

    // registra/atualiza os tokens do usuário
    public void manageUserToken(String userEmail, String token, Platform platform) {
        if (userEmail == null || token == null || token.isBlank() || platform == null) throw new DomainValidationException("Parâmetros inválidos");

        UserAccount user = userAccountRepository.findUserByEmail(userEmail);

        if (user == null) {
            throw new EntityNotFoundException("user com o email " + userEmail + " não encontrado");
        }

        Optional<PushNotificationDeviceToken> existingDeviceToken = deviceTokenRepository.findByToken(token);

        PushNotificationDeviceToken deviceToken;
        if (existingDeviceToken.isPresent()) {
            deviceToken = existingDeviceToken.get();

            // caso esteja inativo seta para ativo
            if (!deviceToken.isActive()) {
                deviceToken.setActive(true);
            }

            deviceToken.setUserAccount(user);
        } else {
            deviceToken = new PushNotificationDeviceToken();

            deviceToken.setUserAccount(user);
            deviceToken.setToken(token.trim());
        }

        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
    }

    // envia notificação ao firebase
    public void sendPushNotification(PushNotificationCommandDTO pushNotificationCommand) {
        NotificationAudience audience = pushNotificationCommand.notificationAudience();

        Set<String> tokensByAudience = resolveTokensByAudience(pushNotificationCommand);

        if (tokensByAudience == null || tokensByAudience.isEmpty()) {
            logger.info("Nenhum token ativo para o user {}, pulando notificação para a audiência: ", audience);
            return;
        }

        List<String> deviceTokens = tokensByAudience.stream().toList();

        MulticastMessage payload = buildFcmMessage(pushNotificationCommand, deviceTokens);

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(payload);

            logger.info("Tokens enviados ao firebase: {}", response.getSuccessCount());

            if (response.getFailureCount() > 0) {
                List<String> failureTokens = getFailureDeviceTokens(response, deviceTokens);
                logger.error("Falha crítica no FCM para a audiência: {}, {} ", audience, response.getFailureCount());

                if (!failureTokens.isEmpty()) {
                    logger.warn("Desativando {} tokens inválidos no banco.", failureTokens.size());
                    deviceTokenRepository.deactivateTokensByValue(failureTokens);
                }
            }
        } catch (FirebaseMessagingException e) {
            logger.error("Erro no envio da mensagem para o Firebase: {} ", e.getMessagingErrorCode());
        }
    }

    // retorna os tokens que falharam da response
    private static List<String> getFailureDeviceTokens(BatchResponse response, List<String> deviceTokens) {
        List<SendResponse> responses = response.getResponses();
        List<String> failureTokens = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {

                String failedToken = deviceTokens.get(i);

                MessagingErrorCode messagingErrorCode = responses.get(i).getException()
                        .getMessagingErrorCode();

                // usuário removeu o app ou limpou os dados ou formato incorreto do token
                if (messagingErrorCode.equals(MessagingErrorCode.UNREGISTERED) || messagingErrorCode.equals(MessagingErrorCode.INVALID_ARGUMENT)) {
                    logger.info("Processo de desativação do token... motivo: {}", messagingErrorCode);

                    // lista temporária para desativar os tokens
                    failureTokens.add(failedToken);
                }

                if (messagingErrorCode.equals(MessagingErrorCode.QUOTA_EXCEEDED)) {
                    logger.warn("Limite do firebase atingido: {}", messagingErrorCode);
                }
            }
        }
        return failureTokens;
    }

    // converte para o formato FCM do firebase
    private MulticastMessage buildFcmMessage(PushNotificationCommandDTO notificationCommandDTO, List<String> deviceTokens) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            throw new DomainValidationException("[buildFcmMessage] tokens não podem ser vazios");
        }

        Map<String, String> data = notificationCommandDTO.data() != null
                ? new HashMap<>(notificationCommandDTO.data())
                : new HashMap<>();

        Set<String> convertedTokens = new HashSet<>(deviceTokens);

        /*
         * setNotification: notificação padrão para dispositivos móveis
         * setWebpushConfig: notificação para navegadores, caso o user esteja no pc ou navegador.
         * "setLink" faz o direcionamento para a página da viagem ao clicar na notificação
         */
        return MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(notificationCommandDTO.title())
                        .setBody(notificationCommandDTO.message())
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle(notificationCommandDTO.title())
                                .setBody(notificationCommandDTO.message())
                                .build())
                        .setFcmOptions(WebpushFcmOptions.builder()
                                .setLink(notificationCommandDTO.link())
                                .build())
                        .build())
                .putAllData(data)
                .addAllTokens(convertedTokens)
                .build();

    }

    // separa cada tokem com base na audiência dele (a quem deve ser enviado)
    private Set<String> resolveTokensByAudience(PushNotificationCommandDTO command) {
        if (command == null || command.notificationAudience() == null) {
            throw new DomainValidationException("[resolveTokensByAudience] audience não pode ser null");
        }

        return switch (command.notificationAudience()) {
            case CUSTOMER_RESPONSIBLES -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId obrigatório para CUSTOMER_RESPONSIBLES");
                }
                yield notificationRecipientResolver.resolveCustomerResponsibles(command.customerId());
            }

            case ALL_CUSTOMER_USERS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId obrigatório para ALL_CUSTOMER_USERS");
                }
                yield notificationRecipientResolver.resolveAllCustomerUsers(command.customerId());
            }

            case CUSTOMER_STUDENTS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId obrigatório para CUSTOMER_STUDENTS");
                }

                yield notificationRecipientResolver.resolveCustomerStudents(command.customerId());
            }

            case CUSTOMER_DRIVERS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId obrigatório para CUSTOMER_DRIVERS");
                }

                yield notificationRecipientResolver.resolveCustomerDrivers(command.customerId());
            }

            case CUSTOMER_ADMINS -> {
                if (command.customerId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId obrigatório para CUSTOMER_ADMINS");
                }

                yield notificationRecipientResolver.resolveCustomerAdmins(command.customerId());
            }

            case SPECIFIC_STUDENT -> {
                if (command.studentId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] studentId obrigatório para SPECIFIC_STUDENT");
                }

                yield notificationRecipientResolver.resolveSpecificStudent(command.studentId());
            }

            case SPECIFIC_DRIVER -> {
                if (command.driverId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] driverId obrigatório para SPECIFIC_DRIVER");
                }

                yield notificationRecipientResolver.resolveSpecificDriver(command.driverId());
            }

            case PERIOD_STUDENTS -> {
                if (command.customerId() == null || command.shift() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] customerId e shift obrigatórios para PERIOD_STUDENTS");
                }

                yield notificationRecipientResolver.resolvePeriodStudents(command.customerId(), command.shift());
            }

            case TRAVEL_STUDENTS -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] travelId obrigatório para TRAVEL_STUDENTS");
                }

                yield notificationRecipientResolver.resolveTravelStudents(command.travelId());
            }

            case TRAVEL_RESPONSIBLES -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] travelId obrigatório para TRAVEL_RESPONSIBLES");
                }

                yield notificationRecipientResolver.resolveCustomerResponsiblesByTravel(command.travelId());
            }

            case STUDENT_RESPONSIBLE -> {
                if (command.studentId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] studentId obrigatório para STUDENT_RESPONSIBLE");
                }

                yield notificationRecipientResolver.resolveStudentResponsible(command.studentId());
            }

            case EMBARKED_TRAVEL_STUDENTS -> {
                if (command.travelId() == null) {
                    throw new DomainValidationException("[resolveTokensByAudience] travelId obrigatório para EMBARKED_TRAVEL_STUDENTS");
                }

                yield notificationRecipientResolver.resolveEmbarkedTravelStudents(command.travelId());
            }
        };
    }
}
