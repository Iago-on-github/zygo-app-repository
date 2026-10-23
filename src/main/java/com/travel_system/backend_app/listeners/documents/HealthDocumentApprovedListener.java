package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthDocumentApprovedEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthDocumentApprovedListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthDocumentApprovedListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerOnDocumentApproved(HealthDocumentApprovedEvent event) {
        healthDocumentNotificationService.sendNotifyOwnerOnDocumentApproved(event);
    }
}

