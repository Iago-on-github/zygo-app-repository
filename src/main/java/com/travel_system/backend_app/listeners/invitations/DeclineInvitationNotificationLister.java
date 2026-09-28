package com.travel_system.backend_app.listeners.invitations;

import com.travel_system.backend_app.events.invitations.InvitationDeclinedEvent;
import com.travel_system.backend_app.service.InvitationNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DeclineInvitationNotificationLister {

    private final InvitationNotificationService invitationNotificationService;

    public DeclineInvitationNotificationLister(InvitationNotificationService invitationNotificationService) {
        this.invitationNotificationService = invitationNotificationService;
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async("invitationsTaskExecutor")
    public void notifyInviterOnDecline(InvitationDeclinedEvent event) {
        // notificação para o admin que mandou o convite e para o user que revogou
        invitationNotificationService.sendNotifyInvitationDeclinedToAdmin(event);
        invitationNotificationService.sendNotifyInvitationDeclinedToUser(event);
    }
}
