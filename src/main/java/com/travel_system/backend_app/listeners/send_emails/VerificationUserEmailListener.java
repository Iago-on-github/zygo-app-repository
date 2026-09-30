package com.travel_system.backend_app.listeners.send_emails;

import com.travel_system.backend_app.events.send_emails.EmailVerificationRequestedEvent;
import com.travel_system.backend_app.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationUserEmailListener {
    private final Logger log = LoggerFactory.getLogger(VerificationUserEmailListener.class);
    private final EmailService emailService;

    public VerificationUserEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("emailVerificationTaskExecutor")
    public void sendVerificationEmail(EmailVerificationRequestedEvent event) {

        try {
            emailService.sendEmailVerification(event.email(), event.expiresAt(), event.pureToken());
        } catch (Exception e) {
            log.warn("[sendVerificationEmail]: Falha ao enviar email de verificação. UserAccountId: {}", event.userAccountId(), e);
        }
    }

}
