package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopStudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.service.StudentRouteStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-stop-students")
public class StudentRouteStopController {
    
    private final StudentRouteStopService studentRouteStopService;

    public StudentRouteStopController(StudentRouteStopService studentRouteStopService) {
        this.studentRouteStopService = studentRouteStopService;
    }

    @Operation(
            summary = "Consulta os pontos de parada (Route Stops) associados a um estudante em uma Rota Padrão.",
            description = "Retorna a lista de pontos de parada vinculados a um estudante específico dentro de uma Rota Padrão determinada.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas usuários com status `ACTIVE` e perfis de Estudante (`ROLE_USER`), Administrador (`ROLE_ADMIN`) ou Administrador de Plataforma (`ROLE_PLATFORM_ADMIN`) podem acessar este recurso.\n" +
                    "- **Isolamento de Dados (Customer):** O usuário autenticado, a Rota Padrão consultada e o Estudante alvo (em caso de consulta por admin) devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Comportamento por Perfil:**\n" +
                    "  - **Estudante (`ROLE_USER`):** Só pode consultar seus próprios dados. O `studentId` passado na requisição é ignorado e sobrescrito pelo ID do usuário autenticado.\n" +
                    "  - **Administrador:** Pode consultar os dados de qualquer estudante, desde que o estudante alvo pertença ao mesmo `Customer` do administrador autenticado.",
            tags = {"Student Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pontos de parada recuperados com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRouteStopAssociateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. O formato dos IDs (UUID) fornecidos está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui os perfis `ROLE_USER`, `ROLE_ADMIN` ou `ROLE_PLATFORM_ADMIN`;\n" +
                    "- O usuário autenticado está com status `INACTIVE`;\n" +
                    "- O usuário autenticado não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** O estudante alvo ou a Rota Padrão não pertencem ao mesmo `Customer` do usuário autenticado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado. Possíveis causas:\n" +
                    "- Usuário com o e-mail autenticado não existe;\n" +
                    "- Rota Padrão com o ID fornecido não existe;\n" +
                    "- Estudante alvo não encontrado (aplicável apenas em consultas de Administradores);\n" +
                    "- Não existe vínculo (StudentRouteStopAssignment) entre o estudante e a Rota Padrão informada.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{studentId}/route-stops/{standardRouteId}")
    public ResponseEntity<List<StudentRouteStopAssociateResponseDTO>> getStudentRouteStops(
            @Parameter(description = "ID do estudante alvo. Se o usuário for ROLE_USER, este valor é ignorado.", required = true)
            @PathVariable UUID studentId,
            @Parameter(description = "ID da Rota Padrão a ser consultada.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(hidden = true) Authentication auth) {

        String authenticatedEmail = auth.getName();

        List<StudentRouteStopAssociateResponseDTO> response = studentRouteStopService.getStudentRouteStops(authenticatedEmail, studentId, standardRouteId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Consulta os pontos de parada de um estudante em uma Rota Padrão filtrados por período.",
            description = "Retorna os detalhes do ponto de parada vinculado a um estudante específico, dentro de uma Rota Padrão e um Período de Viagem (`TravelPeriod`) determinados.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas usuários com status `ACTIVE` e perfis de Estudante (`ROLE_USER`), Administrador (`ROLE_ADMIN`) ou Administrador de Plataforma (`ROLE_PLATFORM_ADMIN`).\n" +
                    "- **Isolamento de Dados (Customer):** O usuário autenticado e a Rota Padrão consultada devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Filtro Específico:** A consulta exige a combinação exata de `studentId` e `travelPeriod` no corpo da requisição. Se o vínculo para essa combinação específica não existir, a requisição falha.",
            tags = {"Student Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ponto de parada filtrado recuperado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRouteStopAssociateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- O corpo da requisição (`RouteStopStudentsRequestDTO`) está malformado ou com campos obrigatórios nulos;\n" +
                    "- O formato dos IDs (UUID) fornecidos está incorreto.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui os perfis autorizados;\n" +
                    "- O usuário autenticado está com status `INACTIVE` ou não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** A Rota Padrão informada não pertence ao mesmo `Customer` do usuário autenticado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado. Possíveis causas:\n" +
                    "- Usuário com o e-mail autenticado não existe;\n" +
                    "- Rota Padrão com o ID fornecido não existe;\n" +
                    "- **Nenhum vínculo encontrado:** Não existe associação (`StudentRouteStopAssignment`) para a combinação exata de Estudante, Rota Padrão e Período de Viagem informados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> getStudentRouteStopsByPeriodAndStandardRoute(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID da Rota Padrão a ser consultada.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Filtros da consulta: ID do estudante e período da viagem.", required = true)
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(authenticatedEmail, standardRouteId, routeStopStudentsRequestDTO)
);
    }

    @Operation(
            summary = "Associa um estudante a um ponto de parada em uma Rota Padrão específica.",
            description = "Cria o vínculo (`StudentRouteStopAssignment`) entre um estudante, um ponto de parada e uma Rota Padrão para um período de viagem determinado.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas usuários `ACTIVE` com perfis de Estudante (`ROLE_USER`), Administrador (`ROLE_ADMIN`) ou Administrador de Plataforma (`ROLE_PLATFORM_ADMIN`).\n" +
                    "- **Isolamento de Dados (Customer):** O usuário autenticado, o Estudante, o Ponto de Parada e a Rota Padrão devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Limite de Associações:** Um estudante pode estar vinculado a no máximo **3** pontos de parada no total.\n" +
                    "- **Unicidade por Período:** O estudante não pode ter mais de um ponto de parada associado ao mesmo `TravelPeriod` (turno).\n" +
                    "- **Validação de Estado:** O Estudante, o Ponto de Parada e a Rota Padrão devem estar com status `ACTIVE`.\n" +
                    "- **Consistência da Rota:** O `TravelPeriod` informado deve existir na lista de períodos da Rota Padrão, e o Ponto de Parada deve efetivamente fazer parte desta Rota Padrão.",
            tags = {"Student Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estudante associado ao ponto de parada com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRouteStopAssociateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException` ou falha no `@Valid`). Possíveis causas:\n" +
                    "- O campo `studentId` no corpo da requisição é nulo;\n" +
                    "- O estudante já possui um ponto de parada associado neste mesmo turno (`TravelPeriod`);\n" +
                    "- O Ponto de Parada ou a Rota Padrão informados estão com status `INACTIVE`;\n" +
                    "- O Ponto de Parada informado não faz parte da Rota Padrão especificada.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui os perfis autorizados;\n" +
                    "- O usuário autenticado está com status `INACTIVE` ou não possui um `Customer` vinculado;\n" +
                    "- **Violação de Isolamento:** O Estudante, o Ponto de Parada ou a Rota Padrão não pertencem ao mesmo `Customer` do usuário autenticado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado ou regra de domínio violada (`DomainValidationException` / `EntityNotFoundException`). Possíveis causas:\n" +
                    "- Usuário, Estudante, Ponto de Parada ou Rota Padrão não existem no sistema;\n" +
                    "- **Limite Atingido:** O estudante já possui o máximo de 3 pontos de parada vinculados;\n" +
                    "- **Incompatibilidade de Período:** O `TravelPeriod` informado não corresponde a nenhum dos períodos configurados na Rota Padrão.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "Conflito de estado (`InactiveAccountException`). O estudante informado está com status `INACTIVE` no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{routeStopId}/associate/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> associateStudentWithRouteStop(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID do Ponto de Parada (Route Stop) a ser associado.", required = true)
            @PathVariable UUID routeStopId,
            @Parameter(description = "ID da Rota Padrão (Standard Route) de destino.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Dados da associação: ID do estudante e período da viagem (TravelPeriod).", required = true)
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.associateStudentWithRouteStop(authenticatedEmail, routeStopId, standardRouteId, routeStopStudentsRequestDTO));
    }

    @Operation(
            summary = "Atualiza o ponto de parada de um estudante em uma Rota Padrão específica.",
            description = "Substitui o ponto de parada atual de um estudante por um novo, dentro de uma Rota Padrão e período de viagem já existentes.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas usuários `ACTIVE` com perfis de Estudante (`ROLE_USER`), Administrador (`ROLE_ADMIN`) ou Administrador de Plataforma (`ROLE_PLATFORM_ADMIN`).\n" +
                    "- **Isolamento de Dados (Customer):** O usuário autenticado, o Estudante, a Rota Padrão e o **novo** Ponto de Parada devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Pré-requisito de Atualização:** O estudante já deve possuir um vínculo ativo (`StudentRouteStopAssignment`) nesta Rota Padrão para que a troca possa ocorrer.\n" +
                    "- **Validação de Estado:** A Rota Padrão e o novo Ponto de Parada devem estar com status `ACTIVE`.\n" +
                    "- **Consistência da Rota:** O período informado deve existir na Rota Padrão, e o novo Ponto de Parada deve efetivamente fazer parte desta Rota.\n" +
                    "- **Prevenção de Duplicidade:** O estudante não pode ter *outro* vínculo ativo nesta mesma Rota Padrão e no mesmo período (`TravelPeriod`).",
            tags = {"Student Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ponto de parada do estudante atualizado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRouteStopAssociateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException` ou falha no `@Valid`). Possíveis causas:\n" +
                    "- A Rota Padrão ou o novo Ponto de Parada informados estão com status `INACTIVE`;\n" +
                    "- O `TravelPeriod` informado não corresponde a nenhum dos períodos configurados na Rota Padrão.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui os perfis autorizados, está `INACTIVE` ou sem `Customer`;\n" +
                    "- **Violação de Isolamento:** O Estudante, a Rota Padrão ou o novo Ponto de Parada não pertencem ao mesmo `Customer` do usuário autenticado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado ou regra de domínio violada. Possíveis causas:\n" +
                    "- Usuário, Estudante, Rota Padrão ou novo Ponto de Parada não existem no sistema;\n" +
                    "- **Vínculo Inexistente:** O estudante não possui nenhuma associação ativa nesta Rota Padrão para ser atualizada;\n" +
                    "- **Ponto Fora da Rota:** O novo Ponto de Parada não faz parte da Rota Padrão informada;\n" +
                    "- **Duplicidade de Turno (`DomainValidationException`):** O estudante já possui *outro* ponto de parada diferente nesta mesma Rota e no mesmo período informado.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PutMapping("/{studentId}/update/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> updateStudentRouteStops(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID do estudante que terá o ponto de parada atualizado.", required = true)
            @PathVariable UUID studentId,
            @Parameter(description = "ID da Rota Padrão onde a atualização ocorrerá.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Dados da atualização: ID do novo ponto de parada e o período da viagem.", required = true)
            @Valid @RequestBody RouteStopStudentUpdateDTO routeStopStudentUpdateDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.updateStudentRouteStops(authenticatedEmail, studentId, standardRouteId, routeStopStudentUpdateDTO));
    }

    @Operation(
            summary = "Remove a associação de um estudante a um ponto de parada em uma Rota Padrão.",
            description = "Exclui o vínculo (`StudentRouteStopAssignment`) entre um estudante, um ponto de parada específico e uma Rota Padrão para um determinado período.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Perfis Autorizados:** Apenas usuários `ACTIVE` com perfis de Estudante (`ROLE_USER`), Administrador (`ROLE_ADMIN`) ou Administrador de Plataforma (`ROLE_PLATFORM_ADMIN`).\n" +
                    "- **Isolamento de Dados (Customer):** O usuário autenticado, o Estudante, o Ponto de Parada e a Rota Padrão devem pertencer obrigatoriamente ao mesmo `Customer`.\n" +
                    "- **Validação de Estado:** O Ponto de Parada e a Rota Padrão devem estar com status `ACTIVE` no momento da remoção.\n" +
                    "- **Consistência da Rota:** O Ponto de Parada informado deve efetivamente fazer parte da Rota Padrão, e o `TravelPeriod` informado deve corresponder aos períodos configurados nesta rota.\n" +
                    "- **Existência do Vínculo:** O estudante deve possuir um vínculo ativo exatamente com esta combinação (Estudante + Rota Padrão + Ponto de Parada) para que a remoção seja executada.",
            tags = {"Student Route Stops"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Associação removida com sucesso. Retorna os dados do vínculo que foi excluído.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRouteStopAssociateResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (`IllegalArgumentException` ou falha no `@Valid`). Possíveis causas:\n" +
                    "- O corpo da requisição está malformado ou com campos obrigatórios nulos;\n" +
                    "- O Ponto de Parada ou a Rota Padrão informados estão com status `INACTIVE`.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Acesso negado. Possíveis causas:\n" +
                    "- O usuário autenticado não possui os perfis autorizados, está `INACTIVE` ou sem `Customer`;\n" +
                    "- **Violação de Isolamento:** O Estudante, o Ponto de Parada ou a Rota Padrão não pertencem ao mesmo `Customer` do usuário autenticado.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado ou regra de domínio violada. Possíveis causas:\n" +
                    "- Usuário, Estudante, Ponto de Parada ou Rota Padrão não existem no sistema;\n" +
                    "- **Vínculo Inexistente:** O estudante não possui associação ativa com este Ponto de Parada nesta Rota Padrão;\n" +
                    "- **Ponto Fora da Rota:** O Ponto de Parada informado não faz parte da Rota Padrão especificada;\n" +
                    "- **Incompatibilidade de Período (`DomainValidationException`):** O `TravelPeriod` informado não corresponde a nenhum dos períodos configurados na Rota Padrão.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @DeleteMapping("/{routeStopId}/remove/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> removeStudentToRouteStop(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID do Ponto de Parada (Route Stop) que será desvinculado.", required = true)
            @PathVariable UUID routeStopId,
            @Parameter(description = "ID da Rota Padrão (Standard Route) de onde o estudante será removido.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Dados da remoção: ID do estudante e o período da viagem (TravelPeriod) do vínculo.", required = true)
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        StudentRouteStopAssociateResponseDTO response = studentRouteStopService.removeStudentFromRouteStop(
                authenticatedEmail,
                routeStopId,
                standardRouteId,
                routeStopStudentsRequestDTO
        );

        return ResponseEntity.ok().body(response);
    }
}
