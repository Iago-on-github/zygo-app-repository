package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.NotAuthorizedException;
import com.travel_system.backend_app.exceptions.ResourceGoneException;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationReviewDTO;
import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.SensitiveOperationConstants.EXPIRES_SENSITIVE_ENTITY_TTL;
import static com.travel_system.backend_app.service.HmacTokenService.calculateTokenHMAC;

@Service
public class SensitiveOperationApprovalService {
    private Logger log = LoggerFactory.getLogger(SensitiveOperationApprovalService.class);

    private final SensitiveOperationRepository sensitiveOperationRepository;

    private final SensitiveOperationExecutionService sensitiveOperationExecutionService;
    private final ApprovalAuthorizationService approvalAuthorizationService;

    @Value("${secret.hash.token-key}")
    private String secretHashTokenKey;

    public SensitiveOperationApprovalService(SensitiveOperationRepository sensitiveOperationRepository, SensitiveOperationExecutionService sensitiveOperationExecutionService, ApprovalAuthorizationService approvalAuthorizationService) {
        this.sensitiveOperationRepository = sensitiveOperationRepository;
        this.sensitiveOperationExecutionService = sensitiveOperationExecutionService;
        this.approvalAuthorizationService = approvalAuthorizationService;
    }

    /*
    *  responsável pela revisão:
    * - valida header (via interceptor), tempo de expiração e status
    * - recalcula o hmac token
    * */
    public SensitiveOperationReviewDTO review(String token) {
        // recalcula hmac do token
        String calculatedTokenHMAC = calculateTokenHMAC(secretHashTokenKey, token);

        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findByVerificationTokenHash(calculatedTokenHMAC)
                .orElseThrow(() -> new NotAuthorizedException("Token recalculado inválido: sem autorização para essa ação"));

        // verifica se já expirou, salva como expired e lança exception (getExpiresAt é calculado com base na constante, por isso nao é necessário comprar dnv)
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {

            sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.EXPIRED);
            sensitiveOperationRepository.save(sensitiveOperation);

            throw new ResourceGoneException("Recurso expirado");
        }

        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.PENDING) {
            log.warn("[review] Status atual da operação diferente de PENDING. Status atual {} ", sensitiveOperation.getSensitiveOperationStatus());

            throw new IllegalArgumentException("Status atual diferente do requisitado");
        }

        return new SensitiveOperationReviewDTO(sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationType(), sensitiveOperation.getRequestedByUserAccountEmail(), sensitiveOperation.getExpiresAt());
    }

    /*
     *  responsável pela aprovação:
     * - valida header (via interceptor), re-valida status e tempo de expiração
     * */
    public SensitiveOperationResponseDTO approve(UUID id) {
        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entidade SensitiveOperation não encontrada pelo Id: " + id));

        // verifica se já expirou, salva como expired e lança exception (getExpiresAt é calculado com base na constante, por isso nao é necessário comprar dnv)
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {

            sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.EXPIRED);
            sensitiveOperationRepository.save(sensitiveOperation);

            throw new ResourceGoneException("Recurso expirado");
        }

        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.PENDING) {
            log.warn("[approve] Status atual da operação diferente de PENDING. Status atual {} ", sensitiveOperation.getSensitiveOperationStatus());

            throw new IllegalArgumentException("Status atual diferente do requisitado");
        }

        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.APPROVED);
        sensitiveOperation.setApprovedAt(Instant.now());

        SensitiveOperation savedOperation = sensitiveOperationRepository.save(sensitiveOperation);

        String message = "Operação Aprovada com sucesso.";

        return new SensitiveOperationResponseDTO(savedOperation.getId(), savedOperation.getSensitiveOperationStatus(), message, savedOperation.getExpiresAt());
    }

    /*
    *  responsável pela execução
    * - valida header (via interceptor), valida expired e status
    * - chama método de execução para lidar corretamente com o tipo da operação
    * */
    public SensitiveOperationResponseDTO execute(UUID id) {
        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entidade SensitiveOperation não encontrada pelo Id: " + id));

        // verifica se já expirou, salva como expired e lança exception (getExpiresAt é calculado com base na constante, por isso nao é necessário comprar dnv)
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {

            sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.EXPIRED);
            sensitiveOperationRepository.save(sensitiveOperation);

            throw new ResourceGoneException("Recurso expirado");
        }

        // caso não seja approved loga warn e lança exception
        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.APPROVED) {
            log.warn("[execute] Status atual da operação diferente de APPROVED. Status atual {} ", sensitiveOperation.getSensitiveOperationStatus());
            throw new IllegalArgumentException("Status atual diferente do requisitado");
        }

        sensitiveOperationExecutionService.execute(sensitiveOperation);

        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.EXECUTED);
        sensitiveOperation.setExecutedAt(Instant.now());

        SensitiveOperation savedOperation = sensitiveOperationRepository.save(sensitiveOperation);

        String message = "Operação Executada com sucesso.";

        return new SensitiveOperationResponseDTO(savedOperation.getId(), savedOperation.getSensitiveOperationStatus(), message, savedOperation.getExpiresAt());
    }
}
