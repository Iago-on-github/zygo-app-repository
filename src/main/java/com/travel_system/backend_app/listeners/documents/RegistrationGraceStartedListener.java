package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.RegistrationGraceStartedEvent;
import com.travel_system.backend_app.service.RegistrationDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationGraceStartedListener {

    private final RegistrationDocumentNotificationService registrationDocumentNotificationService;

    public RegistrationGraceStartedListener(RegistrationDocumentNotificationService registrationDocumentNotificationService) {
        this.registrationDocumentNotificationService = registrationDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerWhenGracePeriodStarted(RegistrationGraceStartedEvent event) {
        registrationDocumentNotificationService.sendNotifyOwnerWhenGracePeriodStarted(event);
    }
}
