package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.DocumentUploadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadedListener {

    @EventListener
    @Async("DocumentNotificationTaskExecutor")
    public void notifyAdminsOnDocumentUploaded(DocumentUploadedEvent event) {
        // lança notificação aq (criar serviço próprio de documentos-notificação)
    }

}
