package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.model.PendingDocsExclusions;
import com.travel_system.backend_app.repository.PendingDocsExclusionsRepository;
import com.travel_system.backend_app.service.S3PrivateStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ObjectStorageJobs {
    private final Logger log = LoggerFactory.getLogger(ObjectStorageJobs.class);

    private final PendingDocsExclusionsRepository pendingDocsExclusionsRepository;

    private final S3PrivateStorageService privateStorageService;

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 500;

    public ObjectStorageJobs(PendingDocsExclusionsRepository pendingDocsExclusionsRepository, S3PrivateStorageService privateStorageService) {
        this.pendingDocsExclusionsRepository = pendingDocsExclusionsRepository;
        this.privateStorageService = privateStorageService;
    }

    @Scheduled(initialDelay = 5, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void retryPendingDocsDeletions() {
        for (PendingDocsExclusions pending : pendingDocsExclusionsRepository.findAllByAttemptsLessThan(MAX_ATTEMPTS)) {

            // tenta deletar
            try {
                privateStorageService.delete(pending.getStorageKey());
                pendingDocsExclusionsRepository.delete(pending);
            } catch (Exception e) {
                pending.setAttempts(pending.getAttempts() + 1);
                pending.setLastErrorMsg(truncate(e.getMessage()));

                if (pending.getAttempts() >= MAX_ATTEMPTS) {
                    log.error("[retryPendingDeletions] Limite de tentativas atingido. Verificação manual necessária. Key: {}",
                            pending.getStorageKey());
                }
            }
        }

    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
