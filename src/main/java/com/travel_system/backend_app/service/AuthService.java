package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;

    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager, TokenConfig tokenConfig) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.tokenConfig = tokenConfig;
    }

    public LoginResponseDTO signing(LoginRequestDTO loginRequestDto) {
        if (loginRequestDto == null || loginRequestDto.email() == null || loginRequestDto.password() == null) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequestDto.email(), loginRequestDto.password()));
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos. Tente novamente");
        }

        var user = userRepository.findUserByEmail(loginRequestDto.email());

        if (user == null){
            throw new EntityNotFoundException("Email não encontrado. Tente novamente");
        }

        UUID customerId = user.getCustomerId() != null ? user.getCustomerId() : null;

        var tokenResponse = tokenConfig.createAccessToken(loginRequestDto.email(), user.getRoles(), customerId);

        return new LoginResponseDTO(
                tokenResponse.username(),
                tokenResponse.authenticated(),
                tokenResponse.created(),
                tokenResponse.expiration(),
                tokenResponse.accessToken(),
                tokenResponse.refreshToken());
    }

    public RefreshTokenResponseDTO refreshToken(String email, String refreshToken, UUID customerId) {
        boolean rolePlatformAdmin = tokenConfig.getRolesFromToken(refreshToken).contains("ROLE_PLATFORM_ADMIN");

        if (customerId == null && rolePlatformAdmin) {
            final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
            userRepository.findByEmailAndRole(email, ROLE_PLATFORM_ADMIN).orElseThrow(() -> new EntityNotFoundException("Platform Admin não encontrado"));
        } else {
            if (customerId == null) throw new BadCredentialsException("Usuário " + email + " mal formado com customerId ausente");

            userRepository.findByEmailAndCustomerId(email, customerId)
                    .orElseThrow(() -> new EntityNotFoundException("Entidade com o email " + email + " não encontrado"));
        }

        var refreshedToken = tokenConfig.refreshToken(refreshToken);

        return new RefreshTokenResponseDTO(
                refreshedToken.accessToken(),
                refreshedToken.refreshToken(),
                refreshedToken.expiresAt());
    }
}
