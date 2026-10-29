package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.RegistrationExpiringEvent;
import com.travel_system.backend_app.service.RegistrationDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationExpiringListener {

    private final RegistrationDocumentNotificationService registrationDocumentNotificationService;

    public RegistrationExpiringListener(RegistrationDocumentNotificationService registrationDocumentNotificationService) {
        this.registrationDocumentNotificationService = registrationDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerWhenRegistrationExpiring(RegistrationExpiringEvent event) {
       registrationDocumentNotificationService.sendNotifyOwnerWhenRegistrationExpiring(event);
    }
}
