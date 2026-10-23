package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthDocumentExpiredEvent;
import com.travel_system.backend_app.events.documents.HealthDocumentExpiringEvent;
import com.travel_system.backend_app.events.documents.HealthDeclarationOutdatedEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Async("documentNotificationTaskExecutor")
public class HealthDocumentsAlertsListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthDocumentsAlertsListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyDocumentsAboutToExpire(HealthDocumentExpiringEvent event) {
        if (event == null) {
            return;
        }

        healthDocumentNotificationService.sendNotifyOwnerOnDocumentExpiring(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyExpireDocuments(HealthDocumentExpiredEvent event) {
        if (event == null) {
            return;
        }

        healthDocumentNotificationService.sendNotifyOwnerOnDocumentExpire(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyHealthDeclarationOutdated(HealthDeclarationOutdatedEvent event) {
        healthDocumentNotificationService.sendNotifyOwnerOnHealthDeclarationOutdated(event);
    }
}
