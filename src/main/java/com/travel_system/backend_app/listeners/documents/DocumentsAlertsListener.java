package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.DocumentExpiredEvent;
import com.travel_system.backend_app.events.documents.DocumentExpiringEvent;
import com.travel_system.backend_app.events.documents.HealthDeclarationOutdatedEvent;
import com.travel_system.backend_app.service.DocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Async("documentNotificationTaskExecutor")
public class DocumentsAlertsListener {

    private final DocumentNotificationService documentNotificationService;

    public DocumentsAlertsListener(DocumentNotificationService documentNotificationService) {
        this.documentNotificationService = documentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyDocumentsAboutToExpire(DocumentExpiringEvent event) {
        if (event == null) {
            return;
        }

        documentNotificationService.sendNotifyOwnerOnDocumentExpiring(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyExpireDocuments(DocumentExpiredEvent event) {
        if (event == null) {
            return;
        }

        documentNotificationService.sendNotifyOwnerOnDocumentExpire(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyHealthDeclarationOutdated(HealthDeclarationOutdatedEvent event) {
        documentNotificationService.sendNotifyOwnerOnHealthDeclarationOutdated(event);
    }
}
