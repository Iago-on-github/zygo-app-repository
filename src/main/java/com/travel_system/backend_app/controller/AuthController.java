package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenConfig tokenConfig;

    public AuthController(AuthService authService, TokenConfig tokenConfig) {
        this.authService = authService;
        this.tokenConfig = tokenConfig;
    }

    @PostMapping("/signing")
    public ResponseEntity<LoginResponseDTO> signing(@Valid @RequestBody LoginRequestDTO data) {
        var token = authService.signing(data);

        return ResponseEntity.ok().body(token);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDTO> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        String email = tokenConfig.getSubjectFromToken(refreshToken);

        var token = authService.refreshToken(email, refreshToken);

        return ResponseEntity.ok().body(token);
    }
}
