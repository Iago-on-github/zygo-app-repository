package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthPendingItemsReminderEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthPendingItemsReminderListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthPendingItemsReminderListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyOwnerOnHealthPendingItems(HealthPendingItemsReminderEvent event){
        healthDocumentNotificationService.sendPendingItemsReminder(event);
    }
}
