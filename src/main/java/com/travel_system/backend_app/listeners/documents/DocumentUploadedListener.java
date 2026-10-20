package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.DocumentUploadedEvent;
import com.travel_system.backend_app.service.DocumentNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentUploadedListener {

    private final DocumentNotificationService documentNotificationService;

    public DocumentUploadedListener(DocumentNotificationService documentNotificationService) {
        this.documentNotificationService = documentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyAdminsOnDocumentUploaded(DocumentUploadedEvent event) {
        // lança notificação de novo documento
        documentNotificationService.sendNotifyAdminsOnDocumentUploaded(event);
    }

}