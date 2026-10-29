package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.RegistrationApprovedEvent;
import com.travel_system.backend_app.events.documents.RegistrationRejectedEvent;
import com.travel_system.backend_app.service.RegistrationDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationRejectedListener {

    private final RegistrationDocumentNotificationService registrationDocumentNotificationService;

    public RegistrationRejectedListener(RegistrationDocumentNotificationService registrationDocumentNotificationService) {
        this.registrationDocumentNotificationService = registrationDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerWhenRegistrationReject(RegistrationRejectedEvent event) {
        registrationDocumentNotificationService.sendNotifyOwnerWhenRegistrationRejected(event);
    }
}
