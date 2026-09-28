package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.model.enums.InvitationStatus;
import com.travel_system.backend_app.repository.InvitationRepository;
import com.travel_system.backend_app.service.InvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class InvitationJobs {
    private final Logger log = LoggerFactory.getLogger(InvitationJobs.class);

    private final InvitationService invitationService;

    public InvitationJobs(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void invitationExpirationJob() {
        try {
            int count = invitationService.expirePendingInvitations();
            if (count > 0) {
                log.info("[InvitationJobs] Convites expirados: {}", count);
            }
        } catch (Exception e) {
            log.error("[InvitationJobs] Falha ao expirar convites pendentes", e);
        }
    }
}
