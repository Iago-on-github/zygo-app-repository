package com.travel_system.backend_app.utils;

import com.google.firebase.messaging.*;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.interfaces.PushNotificationContent;
import com.travel_system.backend_app.model.PushNotificationDeviceToken;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.dtos.notifications.TravelPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.TravelNotificationAudience;
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
    private final NotificationRecipientResolver recipientResolver;
    private final FirebaseMessaging firebaseMessaging;

    private static final int FCM_MULTICAST_LIMIT = 500;

    private static final Logger logger = LoggerFactory.getLogger(FirebaseNotificationSender.class);

    public FirebaseNotificationSender(PushNotificationDeviceTokenRepository deviceTokenRepository, UserAccountRepository userAccountRepository, NotificationRecipientResolver recipientResolver, FirebaseMessaging firebaseMessaging) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.recipientResolver = recipientResolver;
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


    // registra/atualiza os tokens do usuário
    public void sendTravelNotification(TravelPushNotificationCommandDTO command) {
        dispatch(command, recipientResolver.resolve(command), command.travelNotificationAudience().name());
    }

    public void sendSystemNotification(SystemPushNotificationCommandDTO command) {
        dispatch(command, recipientResolver.resolve(command), command.systemNotificationAudience().name());
    }

    // atua como núcleo genérico para as notificações
    private void dispatch(PushNotificationContent content, Set<String> tokens, String audience) {
        if (tokens == null || tokens.isEmpty()) {
            logger.info("Nenhum token ativo para a audiência {}, pulando notificação", audience);
            return;
        }

        List<String> allTokens = List.copyOf(tokens);
        List<String> invalidTokens = new ArrayList<>();

        for (int start = 0; start < allTokens.size(); start += FCM_MULTICAST_LIMIT) {
            // divide em lotes de FCM_MULTICAST_LIMIT. Math.min protege o último lote parcial
            List<String> batch = allTokens.subList(start, Math.min(start + FCM_MULTICAST_LIMIT, allTokens.size()));

            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(buildFcmMessage(content, batch));
                logger.info("FCM [{}]: {} enviados, {} falhas", audience, response.getSuccessCount(), response.getFailureCount());

                if (response.getFailureCount() > 0) {
                    invalidTokens.addAll(getInvalidTokens(response, batch));
                }
            } catch (FirebaseMessagingException e) {
                logger.error("Erro no envio ao Firebase [{}]: {}", audience, e.getMessagingErrorCode(), e);
            }
        }

        if (!invalidTokens.isEmpty()) {
            logger.warn("Desativando {} tokens inválidos", invalidTokens.size());
            deviceTokenRepository.deactivateTokensByValue(invalidTokens);
        }
    }

    // a posição i da resposta corresponde à posição i do lote enviado
    public static List<String> getInvalidTokens(BatchResponse response, List<String> batch) {
        List<String> invalid = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) continue;

            MessagingErrorCode msgErrCode = sendResponse.getException().getMessagingErrorCode();

            // mapeamento de erros + aviso de excesso de quota
            if (msgErrCode == MessagingErrorCode.UNREGISTERED || msgErrCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalid.add(batch.get(i));
            } else if (msgErrCode == MessagingErrorCode.QUOTA_EXCEEDED) {
                logger.warn("Limite do Firebase atingido");
            }
        }

        return invalid;
    }

    private MulticastMessage buildFcmMessage(PushNotificationContent content, List<String> tokens) {
        Map<String, String> data = content.data() != null ? new HashMap<>(content.data()) : new HashMap<>();

        WebpushConfig.Builder webpush = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder()
                        .setTitle(content.title())
                        .setBody(content.message())
                        .build());

        if (content.link() != null) {
            webpush.setFcmOptions(WebpushFcmOptions.builder().setLink(content.link()).build());
        }

        return MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(content.title())
                        .setBody(content.message())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(content.priority() == Priority.HIGH
                                ? AndroidConfig.Priority.HIGH
                                : AndroidConfig.Priority.NORMAL)
                        .build())
                .setWebpushConfig(webpush.build())
                .putAllData(data)
                .addAllTokens(tokens)
                .build();
    }
}
