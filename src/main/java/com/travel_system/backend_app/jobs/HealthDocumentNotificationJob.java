package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.service.HealthDocumentReminderService;
import com.travel_system.backend_app.service.HealthPendingItemsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthDocumentNotificationJob {
    private final Logger log = LoggerFactory.getLogger(HealthDocumentNotificationJob.class);

    private final HealthDocumentReminderService healthDocumentReminderService;
    private final HealthPendingItemsService healthPendingItemsService;

    public HealthDocumentNotificationJob(HealthDocumentReminderService healthDocumentReminderService, HealthPendingItemsService healthPendingItemsService) {
        this.healthDocumentReminderService = healthDocumentReminderService;
        this.healthPendingItemsService = healthPendingItemsService;
    }

    // lembretes de vencimento dos documentos todos os dias
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenExpiring() {
        healthDocumentReminderService.sendExpiringReminders();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenExpire() {
        healthDocumentReminderService.sendExpireReminders();
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void sendDocumentNotificationWhenOutdatedTermComingSoon() {
        healthDocumentReminderService.markOutdatedDeclarations();
    }

    // lembretes de pendências de saúde de semana em semana, na segunda, as 09h
    @Scheduled(cron = "0 0 9 * * MON", zone = "America/Sao_Paulo")
    public void notifyHealthPendingItems() {
        healthPendingItemsService.sendPendingItemsReminders();
    }
}
