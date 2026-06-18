package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.model.dtos.request.LoginRequestDTO;
import com.travel_system.backend_app.model.dtos.response.LoginResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RefreshTokenResponseDTO;
import com.travel_system.backend_app.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

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

    @Operation(
            summary = "Renovar token de acesso (Refresh Token)",
            description = "Recebe um Refresh Token válido no cabeçalho da requisição e gera um novo par de tokens (Access Token e Refresh Token) sem a necessidade de o usuário reinserir suas credenciais. " +
                    "**Rota pública (não requer o Bearer Token no cabeçalho, apenas o X-Refresh-Token).**",
            tags = {"Authentication"}
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RefreshTokenResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. O Refresh Token fornecido é inválido, foi violado ou já expirou.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado. O e-mail vinculado ao Refresh Token não existe mais na base de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDTO> refreshToken(@Parameter(description = "RefreshToken enviado no cabeçalho da requisição.", required = true)
                                                                    @RequestHeader("X-Refresh-Token") String refreshToken) {
        String email = tokenConfig.getSubjectFromToken(refreshToken);
        UUID customerId = tokenConfig.getCustomerIdFromToken(refreshToken);

        var token = authService.refreshToken(email, refreshToken, customerId);

        return ResponseEntity.ok().body(token);
    }
}
