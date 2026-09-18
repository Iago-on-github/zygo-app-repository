package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.Permissions;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.security.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.security.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.security.RefreshTokenResponseDTO;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;

    private final UserProfileResolverService userProfileResolverService;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;

    public AuthService(UserAccountRepository userAccountRepository, UserProfileResolverService userProfileResolverService, AuthenticationManager authenticationManager, TokenConfig tokenConfig) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileResolverService = userProfileResolverService;
        this.authenticationManager = authenticationManager;
        this.tokenConfig = tokenConfig;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO signing(LoginRequestDTO loginRequestDto) {
        // valdiações
        if (loginRequestDto == null || loginRequestDto.email() == null || loginRequestDto.password() == null) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        // processa a autenticação
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequestDto.email(), loginRequestDto.password()));
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos. Tente novamente");
        }

        var userAccount = userAccountRepository.findUserByEmail(loginRequestDto.email());

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
        return tokenConfig.createAccessToken(loginRequestDto.email(), roles, customerId, userAccount.getUserAccountType());
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
}
