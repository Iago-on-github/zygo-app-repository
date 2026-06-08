package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.AdministratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admins")
public class AdministratorController {

    private final AdministratorService administratorService;

    public AdministratorController(AdministratorService administratorService) {
        this.administratorService = administratorService;
    }

    @Operation(
            summary = "Listar todos os Administradores",
            description = "Retorna uma List com todos os administradores cadastrados no sistema. " +
                    "Requer, obrigatoriamente, autenticação com perfil de 'ADMIN'. ",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List contento os Administradores retornada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdministratorResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O Usuário autenticado não possui a role 'ADMIN'. ",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/all")
    public ResponseEntity<List<AdministratorResponseDTO>> getAllAdmins() {
        return ResponseEntity.ok().body(administratorService.getAllAdministrators());
    }

    @Operation(
            summary = "Listar administradores por status",
            description = "Retorna uma lista de administradores filtrada pelo status fornecido. " +
                    "**Importante:** Se nenhum status for enviado na requisição, o sistema assumirá por padrão o status ATIVO. " +
                    "Requer autenticação com o perfil de ADMIN.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista filtrada por status retornada com sucesso.",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdministratorResponseDTO.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Não autorizado. O usuário autenticado não possui a permissão 'ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping
    public ResponseEntity<List<AdministratorResponseDTO>> getAdminsByStatus(
            @RequestParam(required = false) GeneralStatus status
    ) {
        return ResponseEntity.ok().body(administratorService.getAllAdministratorsByStatus(status));
    }

    @GetMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> getCurrentAdministrator(Authentication auth) {
        String authEmail = auth.getName();

        return ResponseEntity.ok().body(administratorService.getCurrentAdministrator(authEmail));
    }

    @PostMapping
    public ResponseEntity<AdministratorResponseDTO> createAdministrator(@Valid @RequestBody AdministratorRequestDTO admRequestDTO, UriComponentsBuilder componentsBuilder) {
        AdministratorResponseDTO newAdm = administratorService.createAdministrator(admRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newAdm.id()).toUri();

        return ResponseEntity.created(uri).body(newAdm);
    }

    @PatchMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> updateCurrentAdministrator(@Valid @RequestBody AdministratorUpdateDTO administratorUpdateDto, Authentication auth) {
        String authEmail = auth.getName();

        return ResponseEntity.ok().body(administratorService.updateCurrentAdministrator(authEmail, administratorUpdateDto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateAdministrator(@PathVariable UUID id, @Valid @RequestBody UpdateEntityStatusDTO administratorStatusDTO) {
        administratorService.updateAdministrator(id, administratorStatusDTO.status());
        return ResponseEntity.noContent().build();
    }
}
