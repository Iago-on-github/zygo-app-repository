package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.events.send_emails.EmailVerificationRequestedEvent;
import com.travel_system.backend_app.exceptions.DuplicateResourceException;
import com.travel_system.backend_app.exceptions.EmailAlreadyVerifiedException;
import com.travel_system.backend_app.exceptions.ErrorWithEmailProcessVerificationException;
import com.travel_system.backend_app.model.EmailVerificationToken;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.security.*;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.EmailVerificationTokenRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserProfileResolverService userProfileResolverService;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;

    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserAccountRepository userAccountRepository, EmailVerificationTokenRepository emailVerificationTokenRepository, PasswordEncoder passwordEncoder, UserProfileResolverService userProfileResolverService, AuthenticationManager authenticationManager, TokenConfig tokenConfig, ApplicationEventPublisher eventPublisher) {
        this.userAccountRepository = userAccountRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userProfileResolverService = userProfileResolverService;
        this.authenticationManager = authenticationManager;
        this.tokenConfig = tokenConfig;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void registerAccount(UserAccountRegisterDTO accountRegisterDTO) {
        String email = normalizeEmail(accountRegisterDTO.email());

        if (userAccountRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Esse email já existe no sistema");
        }

        // cria e monta o user account
        UserAccount userAccount = new UserAccount();

        userAccount.setEmail(email);
        userAccount.setPassword(passwordEncoder.encode(accountRegisterDTO.password()));
        userAccount.setTermsAcceptedAt(LocalDate.now());
        userAccount.setUserAccountType(UserAccountType.UNASSIGNED);
        userAccount.setEmailVerified(false);

        try {
            userAccountRepository.saveAndFlush(userAccount);
        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("erro durante a criação de conta: ", e);
        }

        // envia email de verificação
        sendVerificationEmail(userAccount);
    }

    @Transactional
    public void verifyEmail(String token) {
        Instant now = Instant.now();

        if (token == null || token.isBlank()) {
            throw new ErrorWithEmailProcessVerificationException("Link inválido ou expirado");
        }

        // identifica a conta através do token
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(HmacTokenService.calculateSimpleHash(token))
                .orElseThrow(() -> new ErrorWithEmailProcessVerificationException("Link inválido ou expirado"));

        // já usado, invalidado por reenvio ou vencido
        if (verificationToken.getUsedAt() != null || verificationToken.getExpiresAt().isBefore(now)) {
            throw new ErrorWithEmailProcessVerificationException("Link inválido ou expirado");
        }

        UserAccount user = userAccountRepository.findById(verificationToken.getUserAccountId())
                .orElseThrow(() -> new ErrorWithEmailProcessVerificationException("Link inválido ou expirado"));

        // consome o token e invalida quaisquer outros pendentes da mesma conta
        verificationToken.setUsedAt(now);
        emailVerificationTokenRepository.invalidatePendingTokens(user.getId(), now);

        if (user.isEmailVerified()) {
            return;
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);

    }

    @Transactional
    public void resendVerificationEmail() {
        UserAccount user = userAccountRepository.findUserByEmail(getAuthenticatedUserEmail());

        if (user == null) {
            throw new AccessDeniedException("User não autenticado");
        }

        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException("E-mail do usuário já verificado");
        }

        // invalida os links anteriores deixando apenas o novo valer
        emailVerificationTokenRepository.invalidatePendingTokens(user.getId(), Instant.now());

        sendVerificationEmail(user);
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO signing(LoginRequestDTO loginRequestDto) {
        // valdiações
        if (loginRequestDto == null || loginRequestDto.email() == null || loginRequestDto.password() == null) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        String email = normalizeEmail(loginRequestDto.email());

        // processa a autenticação
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, loginRequestDto.password()));
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos. Tente novamente");
        }

        var userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null){
            throw new EntityNotFoundException("Email não encontrado. Tente novamente");
        }

        /*
        * service realiza a validação de quem exatamente está realizando o login e recupera o customerId dela
        * */
        UUID customerId = userProfileResolverService.resolveCustomerId(userAccount);

        // extrai as ROLES
        List<String> roles = userAccount.getPermissions().stream()
                .map(Permissions::getDescription).toList();

        // retorna o token
        return tokenConfig.createAccessToken(email, roles, customerId, userAccount.getUserAccountType());
    }

    @Transactional(readOnly = true)
    public RefreshTokenResponseDTO refreshToken(String refreshToken, UUID customerId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Token de refresh não fornecido.");
        }

        String email = tokenConfig.getSubjectFromToken(refreshToken);

        UserAccount userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null) {
            throw new EntityNotFoundException("UserAccount não encontrado");
        }

        return tokenConfig.refreshToken(refreshToken, userAccount.getUserAccountType());
    }

    private void sendVerificationEmail(UserAccount userAccount) {
        String pureToken = HmacTokenService.generateRandomPureToken();

        String simplePureToken = HmacTokenService.calculateSimpleHash(pureToken);

        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

        EmailVerificationToken emailVerificationToken = new EmailVerificationToken();

        emailVerificationToken.setTokenHash(simplePureToken);
        emailVerificationToken.setUserAccountId(userAccount.getId());
        emailVerificationToken.setExpiresAt(expiresAt);
        emailVerificationToken.setCreatedAt(Instant.now());

        emailVerificationTokenRepository.save(emailVerificationToken);

        // publica evento
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(userAccount.getId(), userAccount.getEmail(), pureToken, emailVerificationToken.getExpiresAt()));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
