package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.events.send_emails.SensitiveOperationCreatedEvent;
import com.travel_system.backend_app.exceptions.SensitiveOperationException;
import com.travel_system.backend_app.exceptions.SensitiveOperationInternalError;
import com.travel_system.backend_app.interfaces.SensitiveOperationData;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationAuthorizationResult;
import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.travel_system.backend_app.config.constants.SensitiveOperationConstants.EXPIRES_SENSITIVE_ENTITY_TTL;
import static com.travel_system.backend_app.service.HmacTokenService.calculateTokenHMAC;
import static com.travel_system.backend_app.service.HmacTokenService.generateRandomPureToken;

@Service
public class SensitiveOperationService {

    private final SensitiveOperationRepository sensitiveOperationRepository;

    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;


    @Value("${secret.hash.token-key}")
    private String secretHashTokenKey;

    public SensitiveOperationService(SensitiveOperationRepository sensitiveOperationRepository, PasswordEncoder passwordEncoder, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.sensitiveOperationRepository = sensitiveOperationRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SensitiveOperationAuthorizationResult createSensitiveOperationAuthorization(String userEmail, SensitiveOperationData data)  {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail não pode ser nulo ou vazio ao registrar uma SensitiveOperation");
        }

        if (data == null || data.sensitiveType() == null) {
            throw new SensitiveOperationException("Data não pode ser null");
        }

        // gera um token aleatoro puro
        String randomPureToken = generateRandomPureToken();

        // gera o verificationTokenHash a partir do calculo HMAC entre o token com a secret key
        String calculateTokenHMAC = calculateTokenHMAC(secretHashTokenKey, randomPureToken);

        SensitiveOperation sensitiveOperation = new SensitiveOperation();

        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new SensitiveOperationInternalError("Ocorreu um erro durante o parse do JSON na operação sensível", e);
        }

        sensitiveOperation.setSensitiveOperationType(data.sensitiveType());
        sensitiveOperation.setRequestedByUserAccountEmail(userEmail);
        sensitiveOperation.setPayload(payload);
        sensitiveOperation.setVerificationTokenHash(calculateTokenHMAC);
        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.PENDING);
        sensitiveOperation.setExpiresAt(Instant.now().plus(EXPIRES_SENSITIVE_ENTITY_TTL));

        SensitiveOperation savedSensitiveOperation = sensitiveOperationRepository.save(sensitiveOperation);

        eventPublisher.publishEvent(new SensitiveOperationCreatedEvent(randomPureToken, data.sensitiveType(), savedSensitiveOperation.getExpiresAt()));

        // disponibiliza o pure token para ser usado em serviços como envio de email
        return new SensitiveOperationAuthorizationResult(randomPureToken, savedSensitiveOperation);
    }

}
