package com.travel_system.backend_app.jobs;

import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import com.travel_system.backend_app.service.SensitiveOperationApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class SensitiveJobs {
    private final Logger log = LoggerFactory.getLogger(SensitiveJobs.class);

    // status que ainda podem expirar
    private static final List<SensitiveOperationStatus> OPEN_STATUSES = List.of(SensitiveOperationStatus.PENDING, SensitiveOperationStatus.APPROVED);

    private final SensitiveOperationRepository sensitiveOperationRepository;

    public SensitiveJobs(SensitiveOperationRepository sensitiveOperationRepository) {
        this.sensitiveOperationRepository = sensitiveOperationRepository;
    }

    /*
     *  responsável por expirar as operações sensíveis vencidas:
     * - roda a cada 10 min, marcando como EXPIRED em lote tudo que venceu
     * - approve/execute já rejeitam pelo expiresAt, então o atraso do job não afeta a segurança
     * - o status no banco serve para relatório e para limpar o payload
     * */
    @Scheduled(initialDelay = 1, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void expireOverdueSensitiveOperations() {
        int expiredCount = sensitiveOperationRepository.expireOverdue(SensitiveOperationStatus.EXPIRED, OPEN_STATUSES, Instant.now());

        if (expiredCount > 0) {
            log.info("[sensitiveExpirationJob] {} operação(ões) sensível(is) marcada(s) como EXPIRED", expiredCount);
        }
    }

}
