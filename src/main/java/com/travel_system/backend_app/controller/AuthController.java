package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(
            summary = "Realiza Login no sistema.",
            description = "Autentica um usuário com e-mail e senha.\n" +
                    "Retorna os tokens de acesso (Access Token) e de atualização (Refresh Token). \n" +
                    "**Rota pública (não requer token no cabeçalho).**",
            tags = {"Authentication"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados válidos no sistema, token gerado e autenticação permitida.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Os dados enviados não estão no formato correto ou violam validações básicas.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Email ou senha inválidos no sistema.", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado no sistema", content = @Content(schema = @Schema(hidden = true))),

    })
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
