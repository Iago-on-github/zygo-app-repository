package com.travel_system.backend_app.listeners.send_emails;

import com.travel_system.backend_app.events.send_emails.SensitiveOperationCreatedEvent;
import com.travel_system.backend_app.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SensitiveOperationEmailListener {

    private final Logger log = LoggerFactory.getLogger(SensitiveOperationEmailListener.class);

    private final EmailService emailService;

    public SensitiveOperationEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("sendSensitiveEmailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSensitiveOperationCreated(SensitiveOperationCreatedEvent event) {

        // aqui evento não vai propagar, apenas logar
        try {
            emailService.sendSensitiveOperationApprovalEmail(event.pureToken(), event.sensitiveOperationType(), event.expiresAt());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de aprovação para operação sensível: {}", event, e);

        }
    }
}
