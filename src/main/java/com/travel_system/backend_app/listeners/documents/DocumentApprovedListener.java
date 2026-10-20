package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.DocumentApprovedEvent;
import com.travel_system.backend_app.service.DocumentNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentApprovedListener {

    private final DocumentNotificationService documentNotificationService;

    public DocumentApprovedListener(DocumentNotificationService documentNotificationService) {
        this.documentNotificationService = documentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerOnDocumentApproved(DocumentApprovedEvent event) {
        documentNotificationService.sendNotifyOwnerOnDocumentApproved(event);
    }
}

