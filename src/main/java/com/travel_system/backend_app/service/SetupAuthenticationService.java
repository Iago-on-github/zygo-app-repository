package com.travel_system.backend_app.service;

import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.events.send_emails.SensitiveOperationCreatedEvent;
import com.travel_system.backend_app.events.send_emails.VerificationUserEmailDTO;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationAuthorizationResult;
import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.SensitiveOperationConstants.EXPIRES_SENSITIVE_ENTITY_TTL;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;
import static com.travel_system.backend_app.service.HmacTokenService.*;

@Service
public class SetupAuthenticationService {
    private final UserAccountRepository userAccountRepository;
    private final SensitiveOperationRepository sensitiveOperationRepository;

    private final RedisSetupAuthenticationService redisSetupAuthenticationService;

    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${secret.hash.token-key}")
    private String secretHashTokenKey;

    public SetupAuthenticationService(UserAccountRepository userAccountRepository, SensitiveOperationRepository sensitiveOperationRepository, RedisSetupAuthenticationService redisSetupAuthenticationService, PasswordEncoder passwordEncoder, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.sensitiveOperationRepository = sensitiveOperationRepository;
        this.redisSetupAuthenticationService = redisSetupAuthenticationService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // verifica se a senha do user logado bate com a senha cadastrada no banco
    public void authenticateSensitiveOperation(String setupPassword) {
        String loggedUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        UserAccount user = userAccountRepository.findUserByEmail(loggedUserEmail);

        if (user == null) {
            throw new EntityNotFoundException("Usuário com o email: " + loggedUserEmail + " não encontrado no banco");
        }

        String password = user.getPassword();

        boolean passwordMatches = passwordEncoder.matches(setupPassword, password);

        if (!passwordMatches) {
            throw new BadCredentialsException("Senha inválida");
        }

        // se matches, cria auth temporaria para a operação
        registryCacheTtlPermission(user.getEmail());
    }

    // verificação de email
    public void userEmailVerification() throws JsonProcessingException {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(authenticatedUserEmail);

        if (user == null) {
            throw new EntityNotFoundException("Usuário não encontrado para o email: " + authenticatedUserEmail);
        }

        String encodedPassword = passwordEncoder.encode(user.getPassword());

        Map<String, String> requestMapPayload = Map.of(user.getEmail(), encodedPassword);

        // realiza a desserialização do payload com base no email e na password
        String payload = objectMapper.writeValueAsString(requestMapPayload);

        // gera um token aleatório puro
        String randomPureToken = generateRandomPureToken();

        // gera um token simples
        String simpleHashToken = calculateSimpleHash(randomPureToken);

        SensitiveOperation sensitiveOperation = new SensitiveOperation();

        sensitiveOperation.setSensitiveOperationType(SensitiveOperationType.EMAIL_VERIFICATION);
        sensitiveOperation.setRequestedByUserAccountEmail(user.getEmail());
        sensitiveOperation.setPayload(payload);
        sensitiveOperation.setVerificationTokenHash(simpleHashToken);
        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.PENDING);
        sensitiveOperation.setExpiresAt(Instant.now().plus(EXPIRES_SENSITIVE_ENTITY_TTL));

        SensitiveOperation savedSensitiveOperation = sensitiveOperationRepository.save(sensitiveOperation);

        // publica evento de email
        VerificationUserEmailDTO verificationUserEmailDTO = new VerificationUserEmailDTO(user.getEmail(), randomPureToken, SensitiveOperationType.EMAIL_VERIFICATION, savedSensitiveOperation.getExpiresAt());

        eventPublisher.publishEvent(verificationUserEmailDTO);
    }

    @Transactional
    public SensitiveOperationAuthorizationResult createSensitiveOperationAuthorization(String userEmail, SensitiveOperationType sensitiveOperationType, PlatformAdministratorRequestDTO platformAdministratorRequestDTO) throws JsonProcessingException {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail não pode ser nulo ou vazio ao registrar uma SensitiveOperation");
        }

        String passwordHash = passwordEncoder.encode(platformAdministratorRequestDTO.password());

        PlatformAdministratorRequestDTO requestPayloadDTO = new PlatformAdministratorRequestDTO(platformAdministratorRequestDTO.email(), passwordHash);

        // cria payload com os dados do user platformAdm a ser criado
        String payload = objectMapper.writeValueAsString(requestPayloadDTO);

        // gera um token aleatoro puro
        String randomPureToken = generateRandomPureToken();

        // gera o verificationTokenHash a partir do calculo HMAC entre o token com a secret key
        String calculateTokenHMAC = calculateTokenHMAC(secretHashTokenKey, randomPureToken);

        SensitiveOperation sensitiveOperation = new SensitiveOperation();

        sensitiveOperation.setSensitiveOperationType(sensitiveOperationType);
        sensitiveOperation.setRequestedByUserAccountEmail(userEmail);
        sensitiveOperation.setPayload(payload);
        sensitiveOperation.setVerificationTokenHash(calculateTokenHMAC);
        sensitiveOperation.setSensitiveOperationStatus(SensitiveOperationStatus.PENDING);
        sensitiveOperation.setExpiresAt(Instant.now().plus(EXPIRES_SENSITIVE_ENTITY_TTL));

        SensitiveOperation savedSensitiveOperation = sensitiveOperationRepository.save(sensitiveOperation);

        eventPublisher.publishEvent(new SensitiveOperationCreatedEvent(randomPureToken, sensitiveOperationType, savedSensitiveOperation.getExpiresAt()));

        // disponibiliza o pure token para ser usado em serviços como envio de email
        return new SensitiveOperationAuthorizationResult(randomPureToken, sensitiveOperation);
    }

    // registra o cache no redis
    private void registryCacheTtlPermission(String userEmail) {
        // registra TTL
        redisSetupAuthenticationService.putTemporarySetupSensitiveOperation(userEmail);
    }

}

/*
* gerencia toda a infra da parte de setup auth p/ operações críticas no sistema
* */
