package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.AdministratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponents;
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
    public ResponseEntity<Page<AdministratorResponseDTO>> getAllAdmins() {
        return ResponseEntity.ok().body(administratorService.getAllAdministrators());
    }

    @Operation(
            summary = "Listar Administradores por status",
            description = "Retorna uma lista de administradores filtrada pelo status fornecido. " +
                    "**Importante:** Se nenhum status for enviado na requisição, o sistema assumirá por padrão o status ATIVO. " +
                    "Requer autenticação com o perfil de ADMIN.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista filtrada por status retornada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdministratorResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping
    public ResponseEntity<Page<AdministratorResponseDTO>> getAdminsByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(administratorService.getAllAdministratorsByStatus(status));
    }

    @Operation(
            summary = "Obter o Administrator Logado",
            description = "Retorna o atual Administrador logado. Requer autenticação com o perfil de ADMIN.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Administrador logado retornado com sucesso",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdministratorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão ADMIN.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> getCurrentAdministrator(Authentication auth) {
        String authEmail = auth.getName();

        return ResponseEntity.ok().body(administratorService.getCurrentAdministrator(authEmail));
    }

    @Operation(
            summary = "Atualiza o Administrador logado",
            description = "Atualiza os campos do atual Administrador logado. Requer autenticação com o perfil de ADMIN.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Administrador logado retornado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdministratorResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- **Entidade não encontrada** Administrador não encontrado no banco de dados;\n" +
                    "- **E-mail ou Telefone** já cadastrados no sistema por outro usuário.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão ADMIN.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Administrador não encontrado no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> updateCurrentAdministrator(@Valid @RequestBody AdministratorUpdateDTO administratorUpdateDto, Authentication auth) {
        String authEmail = auth.getName();

        return ResponseEntity.ok().body(administratorService.updateCurrentAdministrator(authEmail, administratorUpdateDto));
    }

    @Operation(
            summary = "Criar um novo administrador",
            description = "Cadastra um novo administrador no sistema, valida duplicidade de dados e vincula a permissão 'ROLE_ADMIN'. " +
                    "Requer autenticação com o perfil de 'ADMIN'.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Administrador criado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI do administrador criado (ex: /v1/admins/{id})", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                            "- **Campos obrigatórios** inválidos ou não preenchidos devidamente;\n" +
                            "- **E-mail ou Telefone** já cadastrados no sistema por outro usuário;\n" +
                            "- **Permissão 'ROLE_ADMIN'** não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping
    public ResponseEntity<AdministratorResponseDTO> createAdministrator(@Valid @RequestBody AdministratorRequestDTO admRequestDTO, UriComponentsBuilder componentsBuilder) {
        AdministratorResponseDTO newAdm = administratorService.createAdministrator(admRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newAdm.id()).toUri();

        return ResponseEntity.created(uri).body(newAdm);
    }

    @Operation(
            summary = "Criar um novo Administrador da Plataforma",
            description = "Cadastra um novo administrador da Plataforma no sistema, valida duplicidade de dados e vincula a permissão 'ROLE_PLATFORM_ADMIN'." +
                    "Ele é um Administrator comum, porém com maiores permissões capaz de acessar recursos críticos do próprio sistema. Pode ser criado apenas por outro Platform Administrator.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Platform Administrador criado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI do administrador criado (ex: /v1/admins/{id})", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- **Campos obrigatórios** inválidos ou não preenchidos devidamente;\n" +
                    "- **E-mail ou Telefone** já cadastrados no sistema por outro usuário;\n" +
                    "- **Permissão 'ROLE_PLATFORM_ADMIN'** não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_PLATFORM_ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/platformAdm")
    public ResponseEntity<AdministratorResponseDTO> createPlatformAdministrator(@Valid @RequestBody PlatformAdministratorRequestDTO platformAdmRequestDTO, UriComponentsBuilder componentsBuilder) {
        AdministratorResponseDTO platformAdministrator = administratorService.createPlatformAdministrator(platformAdmRequestDTO);
        URI uri = componentsBuilder.path("/{id}").buildAndExpand(platformAdministrator.id()).toUri();

        return ResponseEntity.created(uri).body(platformAdministrator);
    }

    @Operation(
            summary = "Atualiza o status do Administrador",
            description = "Deve atualizar o status do Administrador, que controla a sua atividade/inatividade. Requer, obrigatoriamente, autenticação com perfil de ADMIN.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Status do Administrador modificado com sucesso. Nenhuma resposta é retornada no body.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "Administrador já possui esse status",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão ADMIN.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Administrador não encontrado no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateAdministrator(@PathVariable UUID id, @Valid @RequestBody UpdateEntityStatusDTO administratorStatusDTO) {
        administratorService.updateAdministrator(id, administratorStatusDTO.status());
        return ResponseEntity.noContent().build();
    }
}
