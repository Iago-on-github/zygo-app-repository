package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthDocumentRejectedEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthDocumentRejectedListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthDocumentRejectedListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerOnDocumentRejected(HealthDocumentRejectedEvent event) {
        healthDocumentNotificationService.sendNotifyOwnerOnDocumentRejected(event);
    }
}

