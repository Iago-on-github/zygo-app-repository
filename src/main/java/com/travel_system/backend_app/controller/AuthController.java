package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.dtos.security.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.security.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.security.RefreshTokenResponseDTO;
import com.travel_system.backend_app.model.dtos.security.UserAccountRegisterDTO;
import com.travel_system.backend_app.service.AuthService;
import com.travel_system.backend_app.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenConfig tokenConfig;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, TokenConfig tokenConfig, CurrentUserService currentUserService) {
        this.authService = authService;
        this.tokenConfig = tokenConfig;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> registerAccount(@Valid @RequestBody UserAccountRegisterDTO userAccountRegisterDTO) {
        authService.registerAccount(userAccountRegisterDTO);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerificationEmail() {
        authService.resendVerificationEmail();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signing")
    public ResponseEntity<LoginResponseDTO> signing(@Valid @RequestBody LoginRequestDTO data) {
        var token = authService.signing(data);

        return ResponseEntity.ok().body(token);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDTO> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        boolean isPlatformAdmin = currentUserService.isPlatformAdmin();

        UUID customerId = null;
        if (!isPlatformAdmin) {
            customerId = tokenConfig.getCustomerIdFromToken(refreshToken);
        }

        var token = authService.refreshToken(refreshToken, customerId);

        return ResponseEntity.ok().body(token);
    }
}
