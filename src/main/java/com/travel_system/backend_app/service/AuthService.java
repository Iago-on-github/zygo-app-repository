package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.EmailNotFoundException;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

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

    @SuppressWarnings("rawtypes")
    public ResponseEntity signing(LoginRequestDTO loginRequestDto) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequestDto.email(), loginRequestDto.password()));
        } catch (Exception e) {
            throw new BadCredentialsException("Email ou senha inválidos. Tente novamente");
        }

        var user = userRepository.findUserByEmail(loginRequestDto.email());

        if (user == null){
            throw new EmailNotFoundException("Email não encontrado. Tente novamente");
        }

        var tokenResponse = tokenConfig.createAccessToken(loginRequestDto.email(), user.getRoles());
        return ResponseEntity.ok().body(tokenResponse);
    }

    @SuppressWarnings("rawtypes")
    public ResponseEntity refreshToken(String email, String refreshToken) {
        var user = userRepository.findUserByEmail(email);

        if (user == null) throw new EmailNotFoundException("Email não encontrado. Tente novamente");

        var loginResponse = tokenConfig.refreshToken(refreshToken);

        return ResponseEntity.ok().body(loginResponse);
    }
}
