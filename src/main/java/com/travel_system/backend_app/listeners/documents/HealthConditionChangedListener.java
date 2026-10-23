package com.travel_system.backend_app.listeners.documents;

import com.travel_system.backend_app.events.documents.HealthConditionChangedEvent;
import com.travel_system.backend_app.service.HealthDocumentNotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HealthConditionChangedListener {

    private final HealthDocumentNotificationService healthDocumentNotificationService;

    public HealthConditionChangedListener(HealthDocumentNotificationService healthDocumentNotificationService) {
        this.healthDocumentNotificationService = healthDocumentNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("documentNotificationTaskExecutor")
    public void notifyAdminsOnConditionChanged(HealthConditionChangedEvent event) {
        healthDocumentNotificationService.sendNotifyAdminsOnHealthConditionChanged(event);
    }
}
