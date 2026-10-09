package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.NotAuthorizedException;
import com.travel_system.backend_app.exceptions.ResourceGoneException;
import com.travel_system.backend_app.exceptions.SensitiveOperationException;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * - somente leitura: busca sem lock e não altera o estado da operação
     * */
    @Transactional(readOnly = true)
    public SensitiveOperationReviewDTO review(String token) {
        // recalcula hmac do token
        String calculatedTokenHMAC = calculateTokenHMAC(secretHashTokenKey, token);

        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findByVerificationTokenHash(calculatedTokenHMAC)
                .orElseThrow(() -> new NotAuthorizedException("Link inválido ou expirado"));

        /*
         * verifica se já expirou e lança exception
         *
         * obs.: a marcação como EXPIRED é feita pelo job agendado através de update em lote
         * obs.: getExpiresAt é calculado com base na constante, por isso nao é necessário comparar dnv
         * */
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceGoneException("Recurso expirado");
        }

        // caso não seja pending loga warn e lança exception
        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.PENDING) {
            log.warn("[review] Status atual da operação {} diferente de PENDING. Status atual {} ",
                    sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationStatus());

            throw new SensitiveOperationException("Operação não está disponível para revisão");
        }

        return new SensitiveOperationReviewDTO(sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationType(), sensitiveOperation.getRequestedByUserAccountEmail(), sensitiveOperation.getExpiresAt());
    }

    /*
     *  responsável pela aprovação:
     * - valida header (via interceptor), re-valida status e tempo de expiração
     * - busca a operação com lock pessimista para impedir aprovação dupla concorrente
     * */
    @Transactional
    public SensitiveOperationResponseDTO approve(String token) {
        // recalcula hmac do token
        String calculatedTokenHMAC = calculateTokenHMAC(secretHashTokenKey, token);

        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findByVerificationTokenHashForUpdate(calculatedTokenHMAC)
                .orElseThrow(() -> new NotAuthorizedException("Link inválido ou expirado"));

        /*
         * verifica se já expirou e lança exception
         *
         * obs.: a marcação como EXPIRED é feita pelo job agendado através de update em lote
         * obs.: getExpiresAt é calculado com base na constante, por isso nao é necessário comparar dnv
         * */
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceGoneException("Recurso expirado");
        }

        // caso não seja pending loga warn e lança exception
        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.PENDING) {
            log.warn("[approve] Status atual da operação {} diferente de PENDING. Status atual {} ",
                    sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationStatus());

            throw new SensitiveOperationException("Operação não está disponível para aprovação");
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
     * - busca a operação com lock pessimista para impedir execução dupla concorrente
     * - chama método de execução para lidar corretamente com o tipo da operação
     * - limpa o payload após a execução
     * */
    @Transactional
    public SensitiveOperationResponseDTO execute(String token) {
        // recalcula hmac do token
        String calculatedTokenHMAC = calculateTokenHMAC(secretHashTokenKey, token);

        // busca com lock: uma requisição simultânea espera esta terminar e, quando ler, já encontra EXECUTED
        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findByVerificationTokenHashForUpdate(calculatedTokenHMAC)
                .orElseThrow(() -> new NotAuthorizedException("Link inválido ou expirado"));

        /*
         * verifica se já expirou e lança exception
         *
         * obs.: a marcação como EXPIRED é feita pelo job agendado através de update em lote
         * obs.: getExpiresAt é calculado com base na constante, por isso nao é necessário comparar dnv
         * */
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceGoneException("Recurso expirado");
        }

        // caso não seja approved loga warn e lança exception
        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.APPROVED) {
            log.warn("[execute] Status atual da operação {} diferente de APPROVED. Status atual {} ",
                    sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationStatus());

            throw new SensitiveOperationException("Operação não está disponível para execução");
        }

        sensitiveOperationExecutionService.execute(sensitiveOperation);

        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.EXECUTED);
        sensitiveOperation.setExecutedAt(Instant.now());

        // limpa o payload: após a execução ele não é mais necessário e pode conter dados sensíveis (ex.: hash de senha)
        sensitiveOperation.setPayload(null);

        SensitiveOperation savedOperation = sensitiveOperationRepository.save(sensitiveOperation);

        String message = "Operação Executada com sucesso.";

        return new SensitiveOperationResponseDTO(savedOperation.getId(), savedOperation.getSensitiveOperationStatus(), message, savedOperation.getExpiresAt());
    }

    /*
     *  responsável pela rejeição:
     * - valida header (via interceptor), re-valida status e tempo de expiração
     * - busca a operação com lock pessimista para impedir conflito com uma aprovação simultânea
     * - só operações PENDING podem ser rejeitadas
     * - limpa o payload, pois a operação nunca será executada
     * */
    @Transactional
    public SensitiveOperationResponseDTO reject(String token) {
        // recalcula hmac do token
        String calculatedTokenHMAC = calculateTokenHMAC(secretHashTokenKey, token);

        SensitiveOperation sensitiveOperation = sensitiveOperationRepository.findByVerificationTokenHashForUpdate(calculatedTokenHMAC)
                .orElseThrow(() -> new NotAuthorizedException("Link inválido ou expirado"));

        /*
         * verifica se já expirou e lança exception
         *
         * obs.: a marcação como EXPIRED é feita pelo job agendado através de update em lote
         * obs.: getExpiresAt é calculado com base na constante, por isso nao é necessário comparar dnv
         * */
        if (sensitiveOperation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceGoneException("Recurso expirado");
        }

        // caso não seja pending loga warn e lança exception
        if (sensitiveOperation.getSensitiveOperationStatus() != SensitiveOperationStatus.PENDING) {
            log.warn("[reject] Status atual da operação {} diferente de PENDING. Status atual {} ", sensitiveOperation.getId(), sensitiveOperation.getSensitiveOperationStatus());

            throw new SensitiveOperationException("Operação não está disponível para rejeição");
        }

        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.REJECTED);
        sensitiveOperation.setRejectedAt(Instant.now());

        // limpa o payload: a operação nunca será executada e ele pode conter dados sensíveis (ex.: hash de senha)
        sensitiveOperation.setPayload(null);

        SensitiveOperation savedOperation = sensitiveOperationRepository.save(sensitiveOperation);

        String message = "Operação Rejeitada com sucesso.";

        return new SensitiveOperationResponseDTO(savedOperation.getId(), savedOperation.getSensitiveOperationStatus(), message, savedOperation.getExpiresAt());
    }
}
