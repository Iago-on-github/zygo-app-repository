package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.documents.*;
import com.travel_system.backend_app.model.dtos.notifications.SystemPushNotificationCommandDTO;
import com.travel_system.backend_app.model.enums.Priority;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.SystemNotificationAudience;
import com.travel_system.backend_app.utils.DateTimeFormats;
import com.travel_system.backend_app.utils.FirebaseNotificationSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RegistrationDocumentNotificationService {

    private final OwnerNotificationRecipientResolver ownerNotificationRecipientResolver;

    private final DateTimeFormats dateTimeFormats;

    private final FirebaseNotificationSender firebaseNotificationSender;

    public RegistrationDocumentNotificationService(OwnerNotificationRecipientResolver ownerNotificationRecipientResolver, DateTimeFormats dateTimeFormats, FirebaseNotificationSender firebaseNotificationSender) {
        this.ownerNotificationRecipientResolver = ownerNotificationRecipientResolver;
        this.dateTimeFormats = dateTimeFormats;
        this.firebaseNotificationSender = firebaseNotificationSender;
    }

    public void sendNotifyAdminsOnRegistrationUploaded(RegistrationSubmittedEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        String title = "Nova matrícula de usuário";
        String message = "Uma nova matrícula foi criada recentemente e está pendente a revisão";

        if (dto.requestType() == RegistrationRequestType.UPDATE) {
            title = "Alteração de matrícula";
            message = "Uma matrícula foi atualizada recenemente e está pendente a análise";

        } else if (dto.requestType() == RegistrationRequestType.RENEWAL) {
            title = "Renovação de matrícula";
            message = "Uma matrícula foi renovada recentemente e está pendente a análise";
        }

        String link = "/registration/" + dto.customerId() + "/" + dto.requestType() + "/" + dto.registrationRequestId();

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_SUBMITTED",
                "registrationRequestId", dto.registrationRequestId().toString(),
                "registrationId", dto.registrationId().toString()
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendNotifyOwnerWhenRegistrationApproved(RegistrationApprovedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String validUntilInfo = dateTimeFormats.localDateFormat(dto.endTerm());
        if (dto.endTerm() == null) {
            validUntilInfo = "sem validade definida";
        }

        String title = "Nova atualização sobre a sua matrícula";
        String message = "Sua matrícula foi aprovada! Número: " + dto.registrationNumber() + ", validade: " + validUntilInfo;

        if (dto.type() == RegistrationRequestType.RENEWAL) {
            message = "Sua matrícula (" + dto.registrationNumber() + ") foi renovada e é válida até: " + validUntilInfo;
        } else if (dto.type() == RegistrationRequestType.UPDATE) {
            message = "Os dados da sua matrícula (" + dto.registrationNumber() + ") foram atualizados";
        }

        String link = "/registration/" + dto.customerId() + "/approved/";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_APPROVED",
                "registrationNumber", dto.registrationNumber(),
                "type", dto.type().toString(),
                "endTerm", validUntilInfo
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.ownerBuilt())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyOwnerWhenRegistrationRejected(RegistrationRejectedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String reason = dto.rejectionReason();
        if (reason.length() > 30) {
            reason = dto.rejectionReason().substring(30) + "...";
        }

        String title = "Atualização sobre a sua matrícula";
        String message = "O administrador solicitou as correções: " + reason;
        String link = "/registration/" + dto.customerId() + "/rejected";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_REJECTED",
                "transportRegistrationId", dto.transportRegistrationId().toString()
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.ownerBuilt())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.HIGH, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyOwnerWhenRegistrationClosed(RegistrationClosedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Aviso de matrícula encerrada";
        String message = "A matrícula foi encerrada porque não foi renovada no prazo. Para voltar a usar o app, faça uma nova matrícula.";
        String link = "/registration/" + dto.customerId() + "/closed";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_CLOSED",
                "registrationNumber", dto.registrationNumber()
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.ownerBuilt())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyAdminsWhenRegistrationClosed(RegistrationClosedEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        String title = "Aviso de matrícula encerrada";
        String message = "A matrícula foi encerrada porque não foi renovada no prazo. Número: " + dto.registrationNumber();
        String link = "/registration/" + dto.customerId() + "/closed";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_CLOSED",
                "registrationNumber", dto.registrationNumber()
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.NORMAL, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendNotifyOwnerWhenRegistrationInBlockWarningDays(RegistrationBlockWarningEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        if (!dto.notifyOwner()) {
            return;
        }

        String title = "Seu acesso será bloqueado";
        String message = "Seu acesso será bloqueado após " + dateTimeFormats.localDateFormat(dto.graceEnd()) + ". Renove sua matrícula para continuar conosco.";
        String link = "/registration/" + dto.customerId() + "/block-warning";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_BLOCK_WARNING",
                "registrationNumber", dto.registrationNumber(),
                "graceEnd", dto.graceEnd().toString()
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.ownerBuilt())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.HIGH, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyAdminWhenRegistrationBlockWarningDays(RegistrationBlockWarningEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        if (!dto.notifyAdmin()) {
            return;
        }

        String title = "Matrícula prestes a ser bloqueada";
        String message = "A matrícula (" + dto.registrationNumber() + ") será bloqueada após "+ dateTimeFormats.localDateFormat(dto.graceEnd()) + " por falta de renovação.";
        String link = "/registration/" + dto.customerId() + "/block-warning";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_BLOCK_WARNING",
                "registrationNumber", dto.registrationNumber(),
                "graceEnd", dto.graceEnd().toString()
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.HIGH, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }

    public void sendNotifyOwnerWhenRegistrationExpiring(RegistrationExpiringEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Aviso de matrícula expirando";
        String message = "Você tem " + dto.daysLeft() + " para renovar a sua matrícula.";

        if (dto.hasDraftRenewal()) {
            message = "Restam " + dto.daysLeft() + " para a expiração da sua matrícula. Você iniciou a renovação mas não enviou, finalize o envio.";
        }

        String link = "/registration/" + dto.customerId() + "/expiring";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_EXPIRING",
                "hasDraftRenewal", String.valueOf(dto.hasDraftRenewal()),
                "termEnd", dto.termEnd().toString()
        );

        Priority priority = Priority.NORMAL;
        if (dto.daysLeft() <= 3) {
            priority = Priority.HIGH;
        }

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.owner())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, priority, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyOwnerWhenRenewalWindowOpened(RegistrationRenewalWindowOpenedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Renovação de matrícula disponível";
        String message = "A renovação da sua matrícula já está disponível. Sua matrícula vale até " + dateTimeFormats.localDateFormat(dto.termEnd()) + ". Renove antes dessa data para não haver interrupções.";
        String link = "/registration/" + dto.customerId() + "/renewal-window-opened";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_RENEWAL_WINDOW_OPENED",
                "termEnd", dto.termEnd().toString()
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.owner())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.NORMAL, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyOwnerWhenGracePeriodStarted(RegistrationGraceStartedEvent dto) {
        SystemNotificationAudience specificUserAccount = SystemNotificationAudience.SPECIFIC_USER_ACCOUNT;

        String title = "Aviso de matrícula vencida";
        String message = "Sua matrícula venceu em " + dateTimeFormats.localDateFormat(dto.termEnd()) + ". Você ainda pode usar o app até " + dateTimeFormats.localDateFormat(dto.graceEnd()) + ": renove até lá para não perder acesso.";
        String link = "/registration/" + dto.customerId() + "/grace-period";

        Map<String, String> data = Map.of(
                "eventType", "REGISTRATION_GRACE_PERIOD",
                "termEnd", dto.termEnd().toString(),
                "graceEnd", dto.graceEnd().toString()
        );

        for (UUID userAccountId : ownerNotificationRecipientResolver.resolve(dto.owner())) {
            SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(specificUserAccount, null, null, null, null, userAccountId, title, message, link, Priority.HIGH, data);

            firebaseNotificationSender.sendSystemNotification(command);
        }
    }

    public void sendNotifyAdminsOfPendingRegistrationsWithoutNextTerm(NextTermMissingEvent dto) {
        SystemNotificationAudience allCustomerAdmins = SystemNotificationAudience.ALL_CUSTOMER_ADMINS;

        String title = "Cadastre o próximo período letivo";
        String message = dto.affectedRegistrations() + " matrículas estão com a renovação aberta, mas não podem ser renovadas porque o próximo período letivo ainda não foi cadastrado.";
        String link = "/registration/" + dto.customerId() + "/customer_term";

        Map<String, String> data = Map.of(
                "eventType", "NEXT_TERM_MISSING",
                "affectedRegistrations", String.valueOf(dto.affectedRegistrations())
        );

        SystemPushNotificationCommandDTO command = new SystemPushNotificationCommandDTO(allCustomerAdmins, dto.customerId(), null, null, null, null, title, message, link, Priority.HIGH, data);

        firebaseNotificationSender.sendSystemNotification(command);
    }
}
