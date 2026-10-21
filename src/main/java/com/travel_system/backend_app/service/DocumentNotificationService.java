package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.*;
import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.SystemNotificationAudience;
import com.travel_system.backend_app.utils.DateTimeFormats;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DocumentNotificationService {

    private final HealthNotificationRecipientResolver healthNotificationRecipientResolver;

    private final FirebaseNotificationSender firebaseNotificationSender;
    private final DateTimeFormats timeFormats;

    public DocumentNotificationService(HealthNotificationRecipientResolver healthNotificationRecipientResolver, FirebaseNotificationSender firebaseNotificationSender, DateTimeFormats timeFormats) {
        this.healthNotificationRecipientResolver = healthNotificationRecipientResolver;
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.timeFormats = timeFormats;
    }


    public void sendNotifyAdminsOnDocumentUploaded(DocumentUploadedEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        String title = "Upload de novo documento";
        String message = "Novo documento pendente para revisão";
        String link = "/documents/" + dto.customerId() + "/new";

        Map<String, String> data = Map.of(
                "eventType", "DOCUMENT_UPLOAD",
                "documentId", dto.documentId().toString()
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendNotifyOwnerOnDocumentApproved(DocumentApprovedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Documento aprovado";
        String message;
        String link = "/documents/" + dto.documentId() + "/approved";

        Map<String, String> data = new HashMap<>();

        data.put("eventType", "DOCUMENT_APPROVED");
        data.put("documentId", dto.documentId().toString());
        data.put("ownerId", dto.owner().id().toString());

        if (dto.validUntil() != null) {
            message = "Um Administrador aprovou o seu documento às " + timeFormats.formatInstantDate(dto.reviewedAt()) + ", e é válido até " + dto.validUntil();

            data.put("validUntil", dto.validUntil().toString());

        } else {
            message = "Um Administrador aprovou o seu documento às " + timeFormats.formatInstantDate(dto.reviewedAt());

        }

        for (UUID userAccountId : healthNotificationRecipientResolver.resolve(dto.owner())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, dto.customerId(), null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }

    }

    public void sendNotifyOwnerOnDocumentRejected(DocumentRejectedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Documento rejeitado";
        String message = "Um Administrador rejeitou o seu documento às " + timeFormats.formatInstantDate(dto.reviewedAt()) + ". Verifique a razão na sessão de Documentos";
        String link = "/documents/" + dto.documentId() + "/rejected";

        Map<String, String> data = Map.of(
                "eventType", "DOCUMENT_REJECTED",
                "documentId", dto.documentId().toString(),
                "ownerId", dto.owner().id().toString()
        );

        for (UUID userAccountId : healthNotificationRecipientResolver.resolve(dto.owner())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, dto.customerId(), null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }

    }

    public void sendNotifyAdminsOnHealthConditionChanged(HealthConditionChangedEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        String title = "Uma declaração modificada";
        String message = "Um usuário realizou uma modificação em sua declaração";
        String link = "/conditions/" + dto.customerId() + "/changed";

        Map<String, String> data = Map.of(
                "eventType", "DECLARATION_CHANGED",
                "healthDeclarationId", dto.healthDeclarationId().toString()
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendNotifyOwnerOnDocumentExpiring(DocumentExpiringEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Documento prestes a expirar";
        String message = "Seu documento, que é válido até " + dto.validUntil() + ", expira em " + dto.daysLeft() + " dias.";
        String link = "/document/" + dto.customerId() + "/expiring-soon/";

        Map<String, String> data = Map.of(
                "eventType", "DOCUMENT_EXPIRING_SOON",
                "documentId", dto.documentId().toString(),
                "daysLeft", String.valueOf(dto.daysLeft()),
                "validUntil", String.valueOf(dto.validUntil())
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, dto.customerId(), null, null, null, dto.userAccountId(), title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendPendingItemsReminder(HealthPendingItemsReminderEvent dto) {
        List<UUID> recipients = healthNotificationRecipientResolver.resolve(dto.owner());

        String title = "Pendências de saúde";
        String message = dto.pendingCount() == 1 ? "Existe 1 pendência na ficha de saúde. Toque para ver." : "Existem " + dto.pendingCount() + " pendências na ficha de saúde. Toque para ver.";
        String link = "/document/" + dto.customerId() + "/health/pending-items";

        Map<String, String> data = Map.of(
                "eventType", "HEALTH_PENDING_ITEMS",
                "ownerId", dto.owner().id().toString(),
                "pendingCount", String.valueOf(dto.pendingCount())
        );

        for (UUID userAccountId : recipients) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(SystemNotificationAudience.SPECIFIC_USER_ACCOUNT, dto.customerId(),
                    null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyOwnerOnDocumentExpire(DocumentExpiredEvent dto) {
        List<UUID> resolve = healthNotificationRecipientResolver.resolve(dto.owner());

        String title = "Lembrete de documento expirado";
        String message = "Existe ao menos um documento expirado na sua conta. Verifique o seu espaço de Documentos";
        String link = "/document/" + dto.customerId() + "/expire/";

        Map<String, String> data = Map.of(
                "eventType", "DOCUMENT_EXPIRED",
                "documentId", dto.documentId().toString()
        );

        for (UUID userAccountId : resolve) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(SystemNotificationAudience.SPECIFIC_USER_ACCOUNT, dto.customerId(), null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }

    }

    public void sendNotifyOwnerOnHealthDeclarationOutdated(HealthDeclarationOutdatedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Documentos prestes a expirar";
        String message = "Seus documentos estão prestes a expirar pois um novo período está chegando. Verifique o espaço de documentos";
        String link = "/document/" + dto.customerId() + "/coming-expire/";

        Map<String, String> data = Map.of(
                "eventType", "DOCUMENT_COMING_TERM_EXPIRE",
                "declarationId", dto.declarationId().toString()
        );

        List<UUID> resolve = healthNotificationRecipientResolver.resolve(dto.owner());

        for (UUID userAccountId : resolve) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, dto.customerId(), null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }

    }
}
