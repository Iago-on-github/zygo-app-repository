package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthDocumentUploadedEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthDocumentUploadedListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthDocumentUploadedListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyAdminsOnDocumentUploaded(HealthDocumentUploadedEvent event) {
        // lança notificação de novo documento
        healthDocumentNotificationService.sendNotifyAdminsOnDocumentUploaded(event);
    }

}