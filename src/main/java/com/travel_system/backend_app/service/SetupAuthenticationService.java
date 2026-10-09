package com.travel_system.backend_app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.constants.CacheConstants;
import com.travel_system.backend_app.config.constants.SensitiveOperationConstants;
import com.travel_system.backend_app.exceptions.ReauthenticationRequiredException;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import com.travel_system.backend_app.repository.SensitiveOperationRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SetupAuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final SensitiveOperationRepository sensitiveOperationRepository;

    private final RedisSetupAuthenticationService redisSetupAuthenticationService;

    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    public SetupAuthenticationService(UserAccountRepository userAccountRepository, SensitiveOperationRepository sensitiveOperationRepository, RedisSetupAuthenticationService redisSetupAuthenticationService, RedisTemplate<String, String> redisTemplate, PasswordEncoder passwordEncoder, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.sensitiveOperationRepository = sensitiveOperationRepository;
        this.redisSetupAuthenticationService = redisSetupAuthenticationService;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // verifica se a senha do user logado bate com a senha cadastrada no banco
    public void authenticateSensitiveOperation(String setupPassword, SensitiveOperationType type) {
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

        // permissão temporária e de uso único para esta operação
        redisTemplate.opsForValue().set(buildPermissionKey(user.getEmail(), type), "1", SensitiveOperationConstants.TEMPORARY_AUTH_TTL);
    }

    public void consumeSensitiveOperationPermission(String email, SensitiveOperationType type) {
        Boolean removed = redisTemplate.delete(buildPermissionKey(email, type));

        if (!Boolean.TRUE.equals(removed)) {
            throw new ReauthenticationRequiredException("AUTHENTICATED REQUIRED");
        }
    }

    // registra o cache no redis
    private void registryCacheTtlPermission(String userEmail) {
        // registra TTL
        redisSetupAuthenticationService.putTemporarySetupSensitiveOperation(userEmail);
    }

    private String buildPermissionKey(String email, SensitiveOperationType type) {
        if (email == null || email.isBlank() || type == null) {
            throw new IllegalArgumentException("E-mail e tipo da operação são obrigatórios");
        }

        return CacheConstants.SETUP_AUTH_DELETE_ACCOUNT_KEY + email.toLowerCase().trim() + ":" + type.name();
    }
}

/*
* gerencia toda a infra da parte de setup auth p/ operações críticas no sistema
* */
