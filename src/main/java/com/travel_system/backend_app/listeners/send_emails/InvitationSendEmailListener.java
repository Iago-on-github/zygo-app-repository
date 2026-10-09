package com.travel_system.backend_app.listeners.send_emails;

import com.travel_system.backend_app.events.invitations.InvitationCreatedEvent;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.EmailService;
import com.travel_system.backend_app.service.InvitationNotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Component
public class InvitationSendEmailListener {
    private final Logger log = LoggerFactory.getLogger(InvitationSendEmailListener.class);

    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;

    private final InvitationNotificationService invitationNotificationService;
    private final EmailService emailService;

    public InvitationSendEmailListener(UserAccountRepository userAccountRepository, CustomerRepository customerRepository, InvitationNotificationService invitationNotificationService, EmailService emailService) {
        this.userAccountRepository = userAccountRepository;
        this.customerRepository = customerRepository;
        this.invitationNotificationService = invitationNotificationService;
        this.emailService = emailService;
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    @Async("emailInvitationsTaskExecutor")
    public void onInvitationCreated(InvitationCreatedEvent event) {
        // envia notificação para o user em específico
        invitationNotificationService.sendNotifyInvitationCreatedToUser(event);

        String userAccountEmail = getUserAccountEmail(event.invitedUserAccountId());
        String customerName = getCustomerName(event.customerId());

        // envia ao service de email
        try {
            emailService.sendInvitationCreatedToUser(userAccountEmail, customerName, event.expiresAt(), event.pureToken());
        } catch (Exception e) {
            log.warn("[onInvitationCreated]: Falha ao enviar email de convite. invitationId: {} ", event.invitationId());
        }

    }

    private String getUserAccountEmail(UUID userAccountId) {
        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade não encontrada"));

        return userAccount.getEmail();
    }

    private String getCustomerName(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer não encontrado"));

        return customer.getName();
    }
}
