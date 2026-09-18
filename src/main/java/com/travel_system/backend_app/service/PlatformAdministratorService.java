package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.SensitiveOperation;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.dtos.response.PlatformAdministratorResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationAuthorizationResult;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.PlatformAdministratorRepository;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.travel_system.backend_app.config.constants.SensitiveOperationConstants.EXPIRES_SENSITIVE_ENTITY_TTL;
import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class PlatformAdministratorService {

    private final PlatformAdministratorRepository platformAdministratorRepository;
    private final SensitiveOperationRepository sensitiveOperationRepository;

    private final SetupAuthenticationService setupAuthenticationService;
    private final RedisSetupAuthenticationService redisSetupAuthenticationService;

    private final PasswordEncoder passwordEncoder;

    @Value("${secret.bootstrap-key}")
    private String secretBootstrap;

    @Value("${platform-admin.approval-email}")
    private String bootstrapApprovalEmail;

    public PlatformAdministratorService(PlatformAdministratorRepository platformAdministratorRepository, SensitiveOperationRepository sensitiveOperationRepository, SetupAuthenticationService setupAuthenticationService, RedisSetupAuthenticationService redisSetupAuthenticationService, PasswordEncoder passwordEncoder) {
        this.platformAdministratorRepository = platformAdministratorRepository;
        this.sensitiveOperationRepository = sensitiveOperationRepository;
        this.setupAuthenticationService = setupAuthenticationService;
        this.redisSetupAuthenticationService = redisSetupAuthenticationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SensitiveOperationResponseDTO createPlatformAdm(PlatformAdministratorRequestDTO platformAdministratorRequestDTO) throws JsonProcessingException {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        String email = platformAdministratorRequestDTO.email();

        boolean alreadyPlatformAdmExists = platformAdministratorRepository.existsByEmail(email);

        if (alreadyPlatformAdmExists) {
            throw new IllegalArgumentException("Já existe um Platform ADM com o email: " + email);
        }

        // se ainda não tiver tll no redis = precisa passar pelo setup auth
        if (!redisSetupAuthenticationService.isTemporarySensitiveOperationAuthorized(authenticatedUserEmail)) {
            throw new StepUpRequiredException("Etapa de Setup não realizada");
        }

        // se já existe ttl cria a auth temporaria
        SensitiveOperationAuthorizationResult sensitiveOperationAuthorization = setupAuthenticationService.createSensitiveOperationAuthorization(
                authenticatedUserEmail,
                SensitiveOperationType.CREATE_PLATFORM_ADMIN,
                platformAdministratorRequestDTO);

        SensitiveOperation sensitiveOperation = sensitiveOperationAuthorization.sensitiveOperation();

        return new SensitiveOperationResponseDTO(
                sensitiveOperation.getId(),
                sensitiveOperation.getSensitiveOperationStatus(),
                "Verifique o e-mail de aprovação para concluir a operação",
                sensitiveOperation.getExpiresAt()
        );
    }


    @Transactional
    public SensitiveOperationResponseDTO createFirstPlatformAdministrator(PlatformAdministratorRequestDTO platformAdministratorRequestDTO, HttpServletRequest request) throws JsonProcessingException {
        if (!verifyBootstrapFromHeader(request)) {
            throw new InvalidBootstrapSecretException("Header inválido ou não encontrado.");
        }

        // se já existe algum cadastrado lança exception
        if (platformAdministratorRepository.existsBy()) {
            throw new BootstrapAlreadyCompletedException("Já existe um Administrador da Plataforma cadastrado no sistema.");
        }

        SensitiveOperationAuthorizationResult sensitiveOperationAuthorization = setupAuthenticationService.createSensitiveOperationAuthorization(
                "SYSTEM_BOOTSTRAP",
                SensitiveOperationType.CREATE_PLATFORM_ADMIN,
                platformAdministratorRequestDTO);

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
