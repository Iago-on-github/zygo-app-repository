package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.invitations.InvitationAcceptedEvent;
import com.travel_system.backend_app.events.invitations.InvitationCreatedEvent;
import com.travel_system.backend_app.events.invitations.InvitationDeclinedEvent;
import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.SystemNotificationAudience;
import com.travel_system.backend_app.model.enums.TravelNotificationAudience;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class InvitationNotificationService {

    private final FirebaseNotificationSender firebaseNotificationSender;
    private final EmailService emailService;

    public InvitationNotificationService(FirebaseNotificationSender firebaseNotificationSender, EmailService emailService) {
        this.firebaseNotificationSender = firebaseNotificationSender;
        this.emailService = emailService;
    }

    // notificação para o usuário que foi convidado
    public void sendNotifyInvitationCreatedToUser(InvitationCreatedEvent event) {
        SystemNotificationAudience audience = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Novo Convite";
        String message = "Você recebeu um convite para fazer parte de um novo espaço.";
        String link = "invitations/" + event.invitationId() + "/created";

        Map<String, String> data = Map.of(
                "eventType", "CREATED_INVITATION",
                "invitationId", event.invitationId().toString(),
                "customerId", event.customerId().toString(),
                "invitedUserAccountId", event.invitedUserAccountId().toString(),
                "expiresAt", event.expiresAt().toString()
        );

        SystemPushNotificationCommandDTO user = new SystemPushNotificationCommandDTO(audience, event.customerId(), null, null, null, event.invitedUserAccountId(),  title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(user);
    }

    // notificação para quem invitou o user quando ele aceita o convite
    public void sendNotifyInvitationAcceptedToAdmin(InvitationAcceptedEvent event) {
        SystemNotificationAudience audience = SystemNotificationAudience.SPECIFIC_CUSTOMER_ADMIN;

        String title = "Convite Aceito";
        String message = "Um novo usuário aceitou o seu convite para fazer parte do espaço.";
        String link = "invitations/" + event.invitationId() + "/accept";

        Map<String, String> data = Map.of(
                "eventType", "ACCEPT_INVITATION",
                "invitationId", event.invitationId().toString(),
                "customerId", event.customerId().toString(),
                "invitedBy", event.invitedBy().toString()
        );

        SystemPushNotificationCommandDTO admin = new SystemPushNotificationCommandDTO(audience, event.customerId(), null, event.invitedBy(), null, null,  title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(admin);
    }

    // notificação para aceitou o convite
    public void sendNotifyInvitationAcceptedToUser(@NonNull InvitationAcceptedEvent event) {
        SystemNotificationAudience audience = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Convite Aceito";
        String message = "Você aceitou o convite para fazer parte de um novo espaço.";
        String link = "invitations/" + event.invitationId() + "/accept";

        Map<String, String> data = Map.of(
                "eventType", "ACCEPT_INVITATION",
                "invitationId", event.invitationId().toString(),
                "customerId", event.customerId().toString(),
                "userAccountId", event.userAccountId().toString()
        );

        SystemPushNotificationCommandDTO user = new SystemPushNotificationCommandDTO(audience, event.customerId(), null, null, null, event.userAccountId(),  title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(user);
    }

    // notificação para quem invitou o user que declinou o convite
    public void sendNotifyInvitationDeclinedToAdmin(InvitationDeclinedEvent event) {
        SystemNotificationAudience audience = SystemNotificationAudience.SPECIFIC_CUSTOMER_ADMIN;

        String title = "Convite Declinado";
        String message = "Um usuário anteriormetne convidado recusou o seu convite para fazer parte do espaço.";
        String link = "invitations/" + event.invitationId() + "/decline";

        Map<String, String> data = Map.of(
                "eventType", "DECLINE_INVITATION",
                "invitationId", event.invitationId().toString(),
                "customerId", event.customerId().toString(),
                "invitedBy", event.invitedById().toString()
        );

        SystemPushNotificationCommandDTO admin = new SystemPushNotificationCommandDTO(audience, event.customerId(), null, event.invitedById(), null, null,  title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(admin);
    }

    // notificação para quem declinou o convite
    public void sendNotifyInvitationDeclinedToUser(InvitationDeclinedEvent event) {
        SystemNotificationAudience audience = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Convite Declinado";
        String message = "Você declinou o convite para fazer parte de um novo espaço.";
        String link = "invitations/" + event.invitationId() + "/decline";

        Map<String, String> data = Map.of(
                "eventType", "DECLINED_INVITATION",
                "invitationId", event.invitationId().toString(),
                "customerId", event.customerId().toString(),
                "invitedUserAccountId", event.invitedUserAccountId().toString()
        );

        SystemPushNotificationCommandDTO user = new SystemPushNotificationCommandDTO(audience, event.customerId(), null, null, null, event.invitedUserAccountId(),  title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(user);
    }

}
