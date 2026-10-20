package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.repository.DocumentRepository;
import com.travel_system.backend_app.service.DocumentReminderService;
import com.travel_system.backend_app.service.HealthPendingItemsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentNotificationJob {
    private final Logger log = LoggerFactory.getLogger(DocumentNotificationJob.class);

    private final DocumentReminderService documentReminderService;
    private final HealthPendingItemsService healthPendingItemsService;

    public DocumentNotificationJob(DocumentReminderService documentReminderService, HealthPendingItemsService healthPendingItemsService) {
        this.documentReminderService = documentReminderService;
        this.healthPendingItemsService = healthPendingItemsService;
    }

    // lembretes de vencimento dos documentos todos os dias
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenExpiring() {
        documentReminderService.sendExpiringReminders();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenExpire() {
        documentReminderService.sendExpireReminders();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenOutdatedTermComingSoon() {
        documentReminderService.sendOutdatedReminderWhenTermComingSoon();
    }

    // lembretes de pendências de saúde de semana em semana, na segunda, as 09h
    @Scheduled(cron = "0 0 9 * * MON", zone = "America/Sao_Paulo")
    public void notifyHealthPendingItems() {
        healthPendingItemsService.sendPendingItemsReminders();
    }
}
