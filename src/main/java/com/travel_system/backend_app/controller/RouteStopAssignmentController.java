package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.service.RouteStopAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-assignment")
public class RouteStopAssignmentController {
    private final RouteStopAssignmentService routeStopAssignmentService;

    public RouteStopAssignmentController(RouteStopAssignmentService routeStopAssignmentService) {
        this.routeStopAssignmentService = routeStopAssignmentService;
    }

    @Operation(
            summary = "Associa um Ponto de Parada existente a uma Rota Padrão.",
            description = "Adiciona um novo Ponto de Parada (`RouteStop`) a uma Rota Padrão já existente, em uma sequência específica, sem remover os pontos de parada já associados.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Isolamento de Dados (Customer):** O Ponto de Parada e a Rota Padrão devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Validação de Estado:** Tanto a Rota Padrão quanto o Ponto de Parada devem estar com status `ACTIVE`.\n" +
                    "- **Validação de Sequência:** O número da sequência (`sequence`) deve ser estritamente maior que zero.\n" +
                    "- **Regras de Unicidade:**\n" +
                    "  - O Ponto de Parada informado **não pode** já estar vinculado a esta Rota Padrão.\n" +
                    "  - A sequência (`sequence`) informada **não pode** já estar em uso por outro ponto de parada nesta mesma rota.\n" +
                    "- **Recálculo de Geometria:** Após a associação, o sistema reordena todos os pontos e consulta a API do Mapbox para recalcular a geometria (polyline) oficial da rota.",
            tags = {"Route Stop Assignments"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Ponto de parada associado com sucesso e geometria recalculada. Sem conteúdo no corpo da resposta."),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException`). Possíveis causas:\n" +
                    "- A sequência (`sequence`) informada é menor ou igual a zero;\n" +
                    "- A Rota Padrão ou o Ponto de Parada estão com status `INACTIVE`;\n" +
                    "- O Ponto de Parada informado já está vinculado a esta Rota Padrão;\n" +
                    "- Já existe outro Ponto de Parada ocupando a sequência (`sequence`) informada nesta rota.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (`CustomerMismatchException`). O Ponto de Parada e a Rota Padrão não pertencem ao mesmo Customer.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). A Rota Padrão ou o Ponto de Parada com os IDs fornecidos não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox para recalcular a geometria da rota após a nova associação.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{standardRouteId}/associate/{routeStopId}")
    public ResponseEntity<Void> associateRouteStopWithStandardRoute(
            @Parameter(description = "ID único (UUID) da Rota Padrão que receberá o novo ponto de parada.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "ID único (UUID) do Ponto de Parada a ser associado.", required = true)
            @PathVariable UUID routeStopId,
            @Parameter(description = "Número da sequência (ordem) em que este ponto de parada deve aparecer na rota. Deve ser > 0.", required = true)
            @RequestParam int sequence,
            @Parameter(description = "Indica se este ponto de parada é opcional para os estudantes.", required = true)
            @RequestParam boolean isOptionalSpot) {

        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, sequence, isOptionalSpot);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove um Ponto de Parada de uma Rota Padrão e reordena a sequência.",
            description = "Desvincula um Ponto de Parada (`RouteStop`) específico de uma Rota Padrão. Após a remoção, o sistema reordena automaticamente a sequência (`sequence`) dos pontos de parada restantes (1, 2, 3...) e recalcula a geometria da rota.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Isolamento de Dados (Customer):** O Ponto de Parada e a Rota Padrão devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Validação de Estado:** Tanto a Rota Padrão quanto o Ponto de Parada devem estar com status `ACTIVE`.\n" +
                    "- **Reordenação Automática:** Os pontos de parada restantes terão suas sequências renumeradas consecutivamente para evitar lacunas.\n" +
                    "- **Otimização de Geometria:** Se a remoção deixar a rota sem nenhum ponto de parada, o sistema salva a alteração **sem** consultar a API do Mapbox. Caso contrário, a geometria é recalculada com os waypoints restantes.",
            tags = {"Route Stop Assignments"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Ponto de parada removido com sucesso e sequência reordenada. Sem conteúdo no corpo da resposta."),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException`). A Rota Padrão ou o Ponto de Parada estão com status `INACTIVE`.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (`CustomerMismatchException`). O Ponto de Parada e a Rota Padrão não pertencem ao mesmo Customer.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (`EntityNotFoundException`). A Rota Padrão ou o Ponto de Parada com os IDs fornecidos não existem no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox para recalcular a geometria da rota após a remoção do ponto.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{standardRouteId}/remove/{routeStopId}")
    public ResponseEntity<Void> removeRouteStopWithStandardRoute(
            @Parameter(description = "ID único (UUID) da Rota Padrão da qual o ponto será removido.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "ID único (UUID) do Ponto de Parada a ser desvinculado.", required = true)
            @PathVariable UUID routeStopId) {

        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reordena os pontos de parada de uma Rota Padrão.",
            description = "Atualiza a sequência (`sequence`) dos pontos de parada de uma Rota Padrão existente e recalcula a geometria do trajeto com base na nova ordem.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Payload Completo Obrigatório:** O corpo da requisição **deve conter TODOS** os pontos de parada atualmente vinculados à rota, cada um com seu novo `newSequence`. Omitir um ponto resultará em erro.\n" +
                    "- **Sequência Contígua:** As novas sequências devem ser números inteiros consecutivos, começando obrigatoriamente em 1 (ex: 1, 2, 3). Lacunas ou duplicatas não são permitidas.\n" +
                    "- **Validação de Estado:** A Rota Padrão e todos os seus pontos de parada devem estar com status `ACTIVE`.\n" +
                    "- **Validação de Coordenadas:** Todos os pontos de parada envolvidos devem possuir latitude e longitude válidas para o recálculo da geometria.\n" +
                    "- **Isolamento de Dados (Customer):** Todos os pontos de parada devem pertencer ao mesmo `Customer` da Rota Padrão.\n" +
                    "- **Recálculo de Geometria:** Após a reordenação, o sistema consulta a API do Mapbox para gerar a nova polyline oficial.",
            tags = {"Route Stop Assignments"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pontos de parada reordenados e geometria recalculada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StandardRouteResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException`). O ID da Rota Padrão não pode ser nulo.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado (`CustomerMismatchException`). Um ou mais pontos de parada não pertencem ao mesmo Customer da Rota Padrão.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado ou regra de domínio violada (`DomainValidationException` / `EntityNotFoundException`). Possíveis causas:\n" +
                    "- A Rota Padrão não existe ou está com status `INACTIVE`;\n" +
                    "- A lista de reordenação está vazia, nula ou contém itens nulos;\n" +
                    "- Sequências (`newSequence`) duplicadas, menores ou iguais a zero, ou não consecutivas (ex: 1, 3, 4);\n" +
                    "- IDs de pontos de parada (`routeStopId`) duplicados no payload;\n" +
                    "- O payload não contém todos os pontos de parada atuais da rota;\n" +
                    "- Um ou mais pontos de parada informados não estão associados a esta rota, não existem, estão `INACTIVE` ou não possuem coordenadas válidas.",
                    content = @Content(schema = @Schema(hidden = true))),

            @ApiResponse(responseCode = "502", description = "Bad Gateway (`RecalculateEtaException`). Falha ao comunicar com a API do Mapbox para recalcular a geometria da rota com a nova ordem dos pontos.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{standardRouteId}/reorder")
    public ResponseEntity<StandardRouteResponseDTO> reorderRouteStops(
            @Parameter(description = "ID único (UUID) da Rota Padrão a ser reordenada.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Lista COMPLETA de todos os pontos de parada da rota, com suas novas sequências (newSequence) consecutivas a partir de 1.", required = true)
            @Valid @RequestBody List<RouteStopReorderRequestDTO> routeStopsReorder) {

        return ResponseEntity.ok().body(routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder));
    }
}
