package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.PendingDocsExclusions;
import com.travel_system.backend_app.repository.PendingDocsExclusionsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PendingDocsExclusionsService {

    private final PendingDocsExclusionsRepository pendingDocsExclusionsRepository;

    private static final int MAX_ERROR_LENGTH = 500;

    public PendingDocsExclusionsService(PendingDocsExclusionsRepository pendingDocsExclusionsRepository) {
        this.pendingDocsExclusionsRepository = pendingDocsExclusionsRepository;
    }

    @Transactional
    public void register(String storageKey, Exception err) {
        PendingDocsExclusions pendingDocsExclusions = pendingDocsExclusionsRepository.findByStorageKey(storageKey).orElseGet(() -> {
            PendingDocsExclusions created = new PendingDocsExclusions();
            created.setStorageKey(storageKey);
            created.setAttempts(0);
            return created;
        });

        pendingDocsExclusions.setAttempts(pendingDocsExclusions.getAttempts() + 1);
        pendingDocsExclusions.setLastErrorMsg(truncate(err.getMessage()));

        pendingDocsExclusionsRepository.save(pendingDocsExclusions);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
