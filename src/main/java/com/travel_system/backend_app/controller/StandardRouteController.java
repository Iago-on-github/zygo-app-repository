package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.StandardRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/standard-route")
public class StandardRouteController {
    private final StandardRouteService standardRouteService;

    public StandardRouteController(StandardRouteService standardRouteService) {
        this.standardRouteService = standardRouteService;
    }

    @Operation(
            summary = "Lista as Rotas Padrão do sistema (Paginação fixa).",
            description = "Retorna uma página paginada contendo as Rotas Padrão cadastradas.\n\n" +
                    "### ⚠️ Regras de Negócio:\n" +
                    "- **Restrição de Perfil:** Esta consulta é exclusiva para **Administradores de Plataforma** (`ROLE_PLATFORM_ADMIN`). Administradores comuns (`ROLE_ADMIN`) e Estudantes (`ROLE_USER`) não terão acesso.\n" +
                    "- **Paginação Fixa:** Por limitação atual do endpoint, a resposta é sempre fixada na **página 0** com tamanho de **10 registros** por página, ignorando parâmetros de paginação externos.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de Rotas Padrão recuperada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))), // O Springdoc resolve Page automaticamente
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. O usuário autenticado não possui o perfil 'ROLE_PLATFORM_ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/all")
    public ResponseEntity<Page<StandardRouteResponseDTO>> getAllStandardRoutes() {
        return ResponseEntity.ok().body(standardRouteService.getAllStandardRoutes());
    }

    @Operation(
            summary = "Consulta os detalhes de uma Rota Padrão específica.",
            description = "Retorna todas as informações detalhadas de uma Rota Padrão identificada pelo seu ID, incluindo seus pontos de parada associados, períodos de viagem permitidos e status atual.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- O ID da Rota Padrão deve ser um UUID válido.\n" +
                    "- A Rota Padrão deve existir no sistema.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detalhes da Rota Padrão recuperados com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O formato do ID (UUID) fornecido na URL está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado. A Rota Padrão com o ID especificado não existe no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{standardRouteId}")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteById(
            @Parameter(description = "ID único (UUID) da Rota Padrão a ser consultada.", required = true)
            @PathVariable UUID standardRouteId) {

        return ResponseEntity.ok().body(standardRouteService.getStandardRouteById(standardRouteId));
    }

    @Operation(
            summary = "Lista as Rotas Padrão vinculadas a um Customer específico.",
            description = "Retorna uma página paginada contendo as Rotas Padrão associadas ao ID do Customer informado.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Restrição de Perfil:** Esta consulta é exclusiva para **Administradores de Plataforma** (`ROLE_PLATFORM_ADMIN`).\n" +
                    "- **Paginação Fixa:** A resposta é sempre fixada na **página 0** com tamanho de **10 registros**, conforme padrão do sistema.\n" +
                    "- **Resultado Vazio:** Caso o Customer informado não possua rotas padrão cadastradas, a requisição terá sucesso (200 OK), mas a lista de conteúdo (`content`) virá vazia.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de Rotas Padrão do Customer recuperada com sucesso (pode retornar lista vazia).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O formato do ID (UUID) do Customer fornecido na URL está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. O usuário autenticado não possui o perfil 'ROLE_PLATFORM_ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{customerId}/customer")
    public ResponseEntity<Page<StandardRouteResponseDTO>> getAllStandardRouteByCustomer(
            @Parameter(description = "ID único (UUID) do Customer cujas rotas padrão serão listadas.", required = true)
            @PathVariable UUID customerId) {

        return ResponseEntity.ok().body(standardRouteService.getAllStandardRouteByCustomer(customerId));
    }

    @Operation(
            summary = "Consulta os detalhes e pontos de parada de uma Rota Padrão filtrada por status.",
            description = "Retorna as informações completas de uma Rota Padrão, incluindo sua lista de pontos de parada (assignments), **desde que o status atual da rota corresponda exatamente ao status informado na consulta**.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Filtro de Status Obrigatório:** A rota só será retornada se o seu status no banco de dados for idêntico ao parâmetro `status` enviado na requisição (ex: `ACTIVE`, `INACTIVE`).\n" +
                    "- **Montagem Híbrida:** Os dados base da rota e os pontos de parada são buscados em consultas otimizadas e combinados para formar a resposta.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rota Padrão e seus pontos de parada recuperados com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- O formato do ID (UUID) da rota está incorreto;\n" +
                    "- O valor do parâmetro `status` não é um enum válido (ex: deve ser 'ACTIVE' ou 'INACTIVE').",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado. Possíveis causas:\n" +
                    "- A Rota Padrão com o ID fornecido não existe;\n" +
                    "- **Incompatibilidade de Status:** A rota existe, mas seu status atual no sistema é diferente do status informado no parâmetro da requisição.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{standardRouteId}/route-stops")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteStopPoints(
            @Parameter(description = "ID único (UUID) da Rota Padrão.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Status pelo qual a rota deve ser filtrada (ex: ACTIVE, INACTIVE).", required = true, schema = @Schema(implementation = GeneralStatus.class))
            @RequestParam GeneralStatus status) {

        return ResponseEntity.ok().body(standardRouteService.getStandardRouteStopPoints(standardRouteId, status));
    }

    @Operation(
            summary = "Cria uma nova Rota Padrão com pontos de parada e geometria calculada.",
            description = "Registra uma nova Rota Padrão no sistema, validando a sequência e unicidade dos pontos de parada, e consumindo a API do Mapbox para calcular e salvar a geometria (polyline) oficial do trajeto.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE`.\n" +
                    "- **Isolamento de Dados:** Todos os pontos de parada informados devem pertencer ao mesmo `Customer` do administrador autenticado.\n" +
                    "- **Unicidade:** O nome da rota (`routeName`) deve ser único dentro do mesmo `Customer`.\n" +
                    "- **Validação de Pontos de Parada:**\n" +
                    "  - A lista de pontos de parada não pode estar vazia.\n" +
                    "  - A sequência (`stopSequence`) deve ser obrigatória, maior que zero e **única** (sem duplicatas).\n" +
                    "  - Um mesmo ponto de parada (`routeStopId`) não pode ser adicionado mais de uma vez na mesma rota.\n" +
                    "  - Todos os pontos de parada informados devem existir e estar com status `ACTIVE`.\n" +
                    "- **Dependência Externa:** A criação depende do sucesso da API do Mapbox para calcular a geometria. Falhas nesta etapa resultam em erro 502.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rota Padrão criada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI da rota padrão recém-criada (ex.: /v1/standard-routes/{id})",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException`, `DomainValidationException` ou `DuplicateResourceException`). Possíveis causas:\n" +
                    "- O nome da rota já existe para este Customer;\n" +
                    "- A lista de pontos de parada está vazia;\n" +
                    "- A sequência (`stopSequence`) é nula, menor ou igual a zero, ou possui valores duplicados;\n" +
                    "- O mesmo `routeStopId` foi informado mais de uma vez na lista;\n" +
                    "- Algum dos pontos de parada informados está com status `INACTIVE`.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador;\n" +
                    "- O usuário administrador está com status `INACTIVE`;\n" +
                    "- **Violação de Isolamento:** Algum dos pontos de parada informados não pertence ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). Possíveis causas:\n" +
                    "- O usuário autenticado (por email) não foi encontrado;\n" +
                    "- Um ou mais IDs de pontos de parada (`routeStopId`) informados não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox ou a API retornou uma geometria nula/inválida para as coordenadas fornecidas.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/new")
    public ResponseEntity<StandardRouteResponseDTO> createStandardRoute(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "Dados completos para criação da rota, incluindo origem, destino, períodos e lista de pontos de parada com suas sequências.", required = true)
            @Valid @RequestBody StandardRouteRequestDTO standardRouteRequestDTO,
            UriComponentsBuilder uriComponentsBuilder) {

        String userAuthenticatedEmail = auth.getName();
        StandardRouteResponseDTO newStandardRoute = standardRouteService.createStandardRoute(userAuthenticatedEmail, standardRouteRequestDTO);

        URI uri = uriComponentsBuilder.path("/{id}").buildAndExpand(newStandardRoute.id()).toUri();

        return ResponseEntity.created(uri).body(newStandardRoute);
    }

    @Operation(
            summary = "Atualiza os dados de uma Rota Padrão existente.",
            description = "Realiza uma atualização parcial ou total dos dados de uma Rota Padrão. Permite alterar nome, descrição, períodos e coordenadas de origem/destino.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE`.\n" +
                    "- **Isolamento de Dados:** A Rota Padrão deve pertencer ao mesmo `Customer` do administrador autenticado.\n" +
                    "- **Unicidade do Nome:** O novo nome da rota (se alterado) deve ser único dentro do mesmo `Customer`.\n" +
                    "- **Validação de Coordenadas (Regra Crítica):** As coordenadas de origem e destino são tratadas em pares. Se você informar a `originLatitude`, é **obrigatório** informar a `originLongitude` (e vice-versa). O mesmo vale para o destino. Se nenhuma coordenada for informada, o sistema mantém as coordenadas atuais do banco.\n" +
                    "- **Recálculo de Geometria:** Sempre que a rota é atualizada, o sistema recalcula a geometria (polyline) consultando a API do Mapbox com as coordenadas (novas ou existentes) e os pontos de parada já vinculados.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rota Padrão atualizada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException` ou `NoSuchCoordinates`). Possíveis causas:\n" +
                    "- Já existe outra rota com o mesmo nome para este Customer;\n" +
                    "- **Coordenadas Parciais:** Foi informada apenas a latitude ou apenas a longitude (de origem ou destino), sem o seu par correspondente.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador;\n" +
                    "- O usuário administrador está com status `INACTIVE` ou sem `Customer`;\n" +
                    "- **Violação de Isolamento:** A Rota Padrão informada não pertence ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). O usuário autenticado ou a Rota Padrão com o ID fornecido não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox para recalcular a geometria da rota com as coordenadas fornecidas.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{standardRouteId}/update")
    public ResponseEntity<StandardRouteResponseDTO> updateStandardRoute(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID único (UUID) da Rota Padrão a ser atualizada.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Campos a serem atualizados. Coordenadas de origem e destino devem ser enviadas sempre em pares completos.", required = true)
            @Valid @RequestBody StandardRouteUpdateDTO standardRouteUpdateDTO) {

        String authenticatedEmail = auth.getName();

        StandardRouteResponseDTO response = standardRouteService.updateStandardRoute(
                standardRouteId,
                authenticatedEmail,
                standardRouteUpdateDTO
        );

        return ResponseEntity.ok().body(response);
    }

    @Operation(
            summary = "Atualiza (substitui) os pontos de parada de uma Rota Padrão.",
            description = "Substitui integralmente a lista de pontos de parada (Route Stops) de uma Rota Padrão existente e recalcula a geometria do trajeto com base na nova sequência.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas Administradores (`ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`) com status `ACTIVE`.\n" +
                    "- **Isolamento de Dados (Customer):** A Rota Padrão e **todos** os novos pontos de parada informados devem pertencer ao mesmo `Customer` do administrador autenticado.\n" +
                    "- **Substituição Total:** A operação limpa os pontos de parada antigos e persiste apenas os enviados no corpo da requisição.\n" +
                    "- **Validações Rigorosas de Payload:**\n" +
                    "  - A lista de pontos de parada não pode estar vazia.\n" +
                    "  - A sequência (`stopSequence`) é obrigatória, deve ser maior que zero e **única** (sem duplicatas).\n" +
                    "  - O ID do ponto de parada (`routeStopId`) é obrigatório e **único** na lista (um mesmo ponto não pode aparecer duas vezes).\n" +
                    "  - Todos os pontos de parada informados devem existir no sistema e estar com status `ACTIVE`.\n" +
                    "- **Recálculo de Geometria:** O sistema consulta a API do Mapbox para gerar a nova polyline com base na origem/destino da rota e na nova ordem dos waypoints.",
            tags = {"Standard Routes"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pontos de parada atualizados e geometria recalculada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`DomainValidationException` ou `IllegalArgumentException`). Possíveis causas:\n" +
                    "- A lista de pontos de parada está vazia ou nula;\n" +
                    "- A sequência (`stopSequence`) é nula, menor ou igual a zero, ou possui valores duplicados;\n" +
                    "- O ID do ponto de parada (`routeStopId`) é nulo ou foi informado mais de uma vez na mesma requisição;\n" +
                    "- Algum dos pontos de parada informados está com status `INACTIVE` no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui perfil de Administrador ou está `INACTIVE`;\n" +
                    "- **Violação de Isolamento:** A Rota Padrão ou algum dos novos pontos de parada não pertence ao mesmo `Customer` do administrador.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). Possíveis causas:\n" +
                    "- O usuário autenticado (por email) não foi encontrado;\n" +
                    "- A Rota Padrão com o ID fornecido não existe;\n" +
                    "- Um ou mais IDs de pontos de parada (`routeStopId`) informados não existem no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox para recalcular a geometria da rota com a nova sequência de pontos.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{standardRouteId}/update/stop-points")
    public ResponseEntity<StandardRouteResponseDTO> updateRouteStopPoints(
            @Parameter(description = "ID único (UUID) da Rota Padrão que terá seus pontos de parada substituídos.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "Lista completa e ordenada dos novos pontos de parada que substituirão os atuais.", required = true)
            @Valid @RequestBody StandardRouteStopsUpdateDTO standardRouteStopsUpdateDTO) {

        String authenticatedEmail = auth.getName();

        StandardRouteResponseDTO response = standardRouteService.updateRouteStopPoints(
                standardRouteId,
                authenticatedEmail,
                standardRouteStopsUpdateDTO
        );

        return ResponseEntity.ok().body(response);
    }

}
