package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorCreationPayload;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorCreationRequestDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationAuthorizationResult;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.PlatformAdministratorRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class PlatformAdministratorService {

    private final PlatformAdministratorRepository platformAdministratorRepository;

    private final SetupAuthenticationService setupAuthenticationService;
    private final SensitiveOperationService sensitiveOperationService;
    private final RedisSetupAuthenticationService redisSetupAuthenticationService;

    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper;

    @Value("${secret.bootstrap-key}")
    private String secretBootstrap;

    public PlatformAdministratorService(PlatformAdministratorRepository platformAdministratorRepository, SetupAuthenticationService setupAuthenticationService, SensitiveOperationService sensitiveOperationService, RedisSetupAuthenticationService redisSetupAuthenticationService, PasswordEncoder passwordEncoder, PasswordEncoder passwordEncoder1, ObjectMapper objectMapper) {
        this.platformAdministratorRepository = platformAdministratorRepository;
        this.setupAuthenticationService = setupAuthenticationService;
        this.sensitiveOperationService = sensitiveOperationService;
        this.redisSetupAuthenticationService = redisSetupAuthenticationService;
        this.passwordEncoder = passwordEncoder1;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SensitiveOperationResponseDTO createPlatformAdm(PlatformAdministratorCreationRequestDTO request) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        String normalizeEmail = request.email() != null ? request.email().toLowerCase(Locale.ROOT).trim() : null;

        setupAuthenticationService.consumeSensitiveOperationPermission(authenticatedUserEmail, SensitiveOperationType.CREATE_PLATFORM_ADMIN);

        boolean alreadyPlatformAdmExists = platformAdministratorRepository.existsByEmail(normalizeEmail);

        if (alreadyPlatformAdmExists) {
            throw new DuplicateResourceException("Já existe um Platform ADM com o email: " + normalizeEmail);
        }

        // cria payload com a senha codificada
        PlatformAdministratorCreationPayload payload = new PlatformAdministratorCreationPayload(normalizeEmail, passwordEncoder.encode(request.password()));

        // se já existe ttl cria a auth temporaria
        SensitiveOperationAuthorizationResult sensitiveOperationAuthorization = sensitiveOperationService.createSensitiveOperationAuthorization(
                authenticatedUserEmail, payload);

        SensitiveOperation sensitiveOperation = sensitiveOperationAuthorization.sensitiveOperation();

        return new SensitiveOperationResponseDTO(
                sensitiveOperation.getId(),
                sensitiveOperation.getSensitiveOperationStatus(),
                "Verifique o e-mail de aprovação para concluir a operação",
                sensitiveOperation.getExpiresAt()
        );
    }

    @Transactional
    public SensitiveOperationResponseDTO createFirstPlatformAdministrator(PlatformAdministratorCreationRequestDTO dto, HttpServletRequest request) {
        if (!verifyBootstrapFromHeader(request)) {
            throw new InvalidBootstrapSecretException("Header inválido ou não encontrado.");
        }

        String normalizeEmail = dto.email() != null ? dto.email().toLowerCase(Locale.ROOT).trim() : null;


        // se já existe algum cadastrado lança exception
        if (platformAdministratorRepository.existsBy()) {
            throw new BootstrapAlreadyCompletedException("Já existe um Administrador da Plataforma cadastrado no sistema.");
        }

        // cria payload com a senha codificada
        PlatformAdministratorCreationPayload payload = new PlatformAdministratorCreationPayload(normalizeEmail, passwordEncoder.encode(dto.password()));

        SensitiveOperationAuthorizationResult sensitiveOperationAuthorization = sensitiveOperationService.createSensitiveOperationAuthorization(
                "SYSTEM_BOOTSTRAP",
                payload);

        SensitiveOperation sensitiveOperation = sensitiveOperationAuthorization.sensitiveOperation();

        return new SensitiveOperationResponseDTO(
                sensitiveOperation.getId(),
                sensitiveOperation.getSensitiveOperationStatus(),
                "Verifique o e-mail de aprovação para concluir a operação",
                sensitiveOperation.getExpiresAt()
        );

    }

    private boolean verifyBootstrapFromHeader(HttpServletRequest servletRequest) {
        String secretBootstrapKey = servletRequest.getHeader("X-Bootstrap-secret-key");

        // se existir, valida
        if (secretBootstrapKey != null && !secretBootstrapKey.isBlank()) {
            return secretBootstrapKey.equalsIgnoreCase(secretBootstrap);
        }

        return false;
    }

}
