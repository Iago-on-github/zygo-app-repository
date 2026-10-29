package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.RegistrationBlockWarningEvent;
import com.travel_system.backend_app.service.RegistrationDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationBlockWarningListener {

    private final RegistrationDocumentNotificationService registrationDocumentNotificationService;

    public RegistrationBlockWarningListener(RegistrationDocumentNotificationService registrationDocumentNotificationService) {
        this.registrationDocumentNotificationService = registrationDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyAdminOrOwnerWhenBlockWarning(RegistrationBlockWarningEvent event) {
        registrationDocumentNotificationService.sendNotifyAdminWhenRegistrationBlockWarningDays(event);

        registrationDocumentNotificationService.sendNotifyOwnerWhenRegistrationInBlockWarningDays(event);
    }
}
