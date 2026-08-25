package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.RouteStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.tomcat.util.http.parser.Authorization;
import org.simpleframework.xml.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-stops")
public class RouteStopController {
    private final RouteStopService routeStopService;

    public RouteStopController(RouteStopService routeStopService) {
        this.routeStopService = routeStopService;
    }

    @Operation(
            summary = "Lista os Pontos de Parada (Route Stops) de um Customer específico.",
            description = "Retorna uma lista contendo todos os Pontos de Parada cadastrados e vinculados ao ID do Customer informado.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- O ID do Customer deve ser um UUID válido.\n" +
                    "- **Resultado Vazio:** Caso o Customer informado não possua nenhum ponto de parada cadastrado, a requisição terá sucesso (200 OK), mas a lista retornada estará vazia (`[]`).",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de Pontos de Parada recuperada com sucesso (pode retornar lista vazia).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RouteStopResponseDTO.class, type = "array"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O formato do ID (UUID) do Customer fornecido na URL está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{customerId}/customer")
    public ResponseEntity<List<RouteStopResponseDTO>> getRouteStopsByCustomer(
            @Parameter(description = "ID único (UUID) do Customer cujos pontos de parada serão listados.", required = true)
            @PathVariable UUID customerId) {

        return ResponseEntity.ok().body(routeStopService.getRouteStopsByCustomer(customerId));
    }

    @Operation(
            summary = "Consulta um Ponto de Parada (Route Stop) pelo nome.",
            description = "Retorna os detalhes completos de um Ponto de Parada específico com base no seu nome.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- A busca requer o **nome exato** cadastrado no sistema (geralmente sensível a maiúsculas e minúsculas, dependendo da configuração do banco).\n" +
                    "- Este endpoint retorna um único objeto, diferentemente dos endpoints de listagem que retornam arrays.",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ponto de Parada encontrado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RouteStopResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O nome fornecido na URL está vazio ou em formato incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). Não existe nenhum Ponto de Parada cadastrado com o nome exato informado.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{name}")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopByName(
            @Parameter(description = "Nome exato do Ponto de Parada (Route Stop) a ser consultado.", required = true)
            @PathVariable String name) {

        return ResponseEntity.ok().body(routeStopService.getRouteStopByName(name));
    }

    @Operation(
            summary = "Consulta um Ponto de Parada (Route Stop) pelo ID.",
            description = "Retorna os detalhes completos de um Ponto de Parada específico com base no seu ID único (UUID).\n\n" +
                    "### Regras de Negócio:\n" +
                    "- O ID fornecido deve ser um UUID válido.\n" +
                    "- Este endpoint retorna um único objeto, diferentemente dos endpoints de listagem que retornam arrays.",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ponto de Parada encontrado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RouteStopResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O formato do ID (UUID) fornecido na URL está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). Não existe nenhum Ponto de Parada cadastrado com o ID informado.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{routeStopId}/route")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopById(
            @Parameter(description = "ID único (UUID) do Ponto de Parada (Route Stop) a ser consultado.", required = true)
            @PathVariable UUID routeStopId) {

        return ResponseEntity.ok().body(routeStopService.getRouteStopById(routeStopId));
    }

    @Operation(
            summary = "Cria um novo Ponto de Parada (Route Stop).",
            description = "Registra um novo Ponto de Parada no sistema. Opcionalmente, permite associar estudantes a este ponto no momento da criação.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE` e vinculados a um `Customer`.\n" +
                    "- **Isolamento de Dados:** O novo Ponto de Parada será automaticamente vinculado ao `Customer` do administrador autenticado.\n" +
                    "- **Unicidade:** O nome (`name`) do ponto de parada deve ser único dentro do mesmo `Customer`.\n" +
                    "- **Associação de Estudantes (Opcional):** Se a lista `studentIds` for fornecida no corpo da requisição:\n" +
                    "  - Todos os IDs informados devem existir no sistema.\n" +
                    "  - Todos os estudantes devem estar com status `ACTIVE`.\n" +
                    "  - Todos os estudantes devem pertencer ao mesmo `Customer` do administrador.",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ponto de Parada criado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RouteStopResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI do ponto de parada recém-criado (ex.: /v1/route-stops/{id})",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`DuplicateResourceException` ou falha no `@Valid`). O nome do ponto de parada já existe para este Customer.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador;\n" +
                    "- O usuário administrador está com status `INACTIVE` ou não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** Um ou mais estudantes informados não pertencem ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). O usuário autenticado não foi encontrado ou um/mais IDs de estudantes informados não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "Conflito de estado (`InactiveAccountException`). Um ou mais estudantes informados estão com status `INACTIVE` no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/new")
    public ResponseEntity<RouteStopResponseDTO> createRouteStop(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "Dados para criação do Ponto de Parada. O campo 'studentIds' é opcional, mas se fornecido, passa por validações rigorosas.", required = true)
            @Valid @RequestBody RouteStopRequestDTO routeStopRequestDTO,

            UriComponentsBuilder componentsBuilder) {

        String authenticatedEmail = auth.getName();
        RouteStopResponseDTO newRouteStop = routeStopService.createRouteStop(authenticatedEmail, routeStopRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newRouteStop.id()).toUri();

        return ResponseEntity.created(uri).body(newRouteStop);
    }

    @Operation(
            summary = "Atualiza os dados de um Ponto de Parada (Route Stop) existente.",
            description = "Realiza uma atualização parcial dos dados de um Ponto de Parada, como nome, descrição ou coordenadas.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE` e vinculados a um `Customer`.\n" +
                    "- **Isolamento de Dados:** O Ponto de Parada deve pertencer obrigatoriamente ao mesmo `Customer` do administrador autenticado.\n" +
                    "- **Unicidade do Nome:** O novo nome (se alterado) deve ser único dentro do mesmo `Customer`.\n" +
                    "- **Regra de Coordenadas (Crítica):** As coordenadas de latitude e longitude são tratadas como um par indivisível. Se você informar a `latitude`, é **obrigatório** informar a `longitude` (e vice-versa). Se nenhuma for informada, as coordenadas atuais são mantidas.",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ponto de Parada atualizado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RouteStopResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`DuplicateResourceException`, `NoSuchCoordinates` ou falha no `@Valid`). Possíveis causas:\n" +
                    "- Já existe outro ponto de parada com o mesmo nome para este Customer;\n" +
                    "- **Coordenadas Parciais:** Foi informada apenas a latitude ou apenas a longitude, sem o seu par correspondente.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador;\n" +
                    "- O usuário administrador está com status `INACTIVE` ou não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** O Ponto de Parada informado não pertence ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). O usuário autenticado ou o Ponto de Parada com o ID fornecido não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{routeStopId}/update")
    public ResponseEntity<RouteStopResponseDTO> updateRouteStop(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID único (UUID) do Ponto de Parada a ser atualizado.", required = true
            )
            @PathVariable UUID routeStopId,
            @Parameter(description = "Campos a serem atualizados. Se alterar a localização, latitude e longitude devem ser enviadas juntas.", required = true)
            @Valid @RequestBody RouteStopUpdateDTO routeStopUpdateDTO) {

        String authenticatedEmail = auth.getName();

        RouteStopResponseDTO response = routeStopService.updateRouteStop(
                authenticatedEmail,
                routeStopId,
                routeStopUpdateDTO
        );

        return ResponseEntity.ok().body(response);
    }

    @Operation(
            summary = "Atualiza o status de um Ponto de Parada (Route Stop).",
            description = "Alterna o status de um Ponto de Parada específico (ex: de `ACTIVE` para `INACTIVE` ou vice-versa).\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE` e vinculados a um `Customer`.\n" +
                    "- **Isolamento de Dados:** O Ponto de Parada deve pertencer obrigatoriamente ao mesmo `Customer` do administrador autenticado.\n" +
                    "- **Validação de Redundância:** Não é permitido enviar uma requisição para alterar o status para o valor que o ponto de parada já possui atualmente.",
            tags = {"Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Status do Ponto de Parada atualizado com sucesso. Sem conteúdo no corpo da resposta."),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`DuplicateResourceException` ou falha no `@Valid`). O Ponto de Parada já possui o status informado na requisição.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador;\n" +
                    "- O usuário administrador está com status `INACTIVE` ou não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** O Ponto de Parada informado não pertence ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). O usuário autenticado ou o Ponto de Parada com o ID fornecido não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{routeStopId}/status")
    public ResponseEntity<Void> updateRouteStopStatus(
            @Parameter(description = "ID único (UUID) do Ponto de Parada (Route Stop) que terá o status alterado.", required = true)
            @PathVariable UUID routeStopId,
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "Novo status a ser aplicado ao Ponto de Parada (ex: ACTIVE, INACTIVE).", required = true, schema = @Schema(implementation = GeneralStatus.class))
            @RequestParam GeneralStatus status) {

        String authenticatedEmail = auth.getName();

        routeStopService.updateRouteStopStatus(routeStopId, authenticatedEmail, status);

        return ResponseEntity.noContent().build();
    }
}
