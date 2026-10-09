package com.travel_system.backend_app.listeners.invitations;

import com.travel_system.backend_app.events.invitations.InvitationAcceptedEvent;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.EmailService;
import com.travel_system.backend_app.service.InvitationNotificationService;
import com.travel_system.backend_app.service.InvitationService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class AcceptInvitationNotificationListener {
    private final Logger log = LoggerFactory.getLogger(AcceptInvitationNotificationListener.class);

    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;

    private final InvitationNotificationService invitationNotificationService;
    private final EmailService emailService;

    public AcceptInvitationNotificationListener(UserAccountRepository userAccountRepository, CustomerRepository customerRepository, InvitationNotificationService invitationNotificationService, EmailService emailService) {
        this.userAccountRepository = userAccountRepository;
        this.customerRepository = customerRepository;
        this.invitationNotificationService = invitationNotificationService;
        this.emailService = emailService;
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async("emailInvitationsTaskExecutor")
    public void notifyInviterOnAccept(InvitationAcceptedEvent event) {
        // notificação para o admin que mandou o convite e para o user que aceitou
        invitationNotificationService.sendNotifyInvitationAcceptedToAdmin(event);
        invitationNotificationService.sendNotifyInvitationAcceptedToUser(event);

        // manda email de boas vindas para o estudante
        try {
            String userAccountEmail = getUserAccountEmail(event.userAccountId());
            String customerName = getCustomerName(event.customerId());

            emailService.sendInvitationAcceptedToUser(userAccountEmail, customerName, event.acceptedInvitationAt());
        } catch (Exception e) {
            log.warn("[notifyInviterOnAccept]: Falha ao enviar email de convite. invitationId: {} ", event.invitationId());
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
