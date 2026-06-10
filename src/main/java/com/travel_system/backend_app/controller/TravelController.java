package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.service.TravelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/travel")
public class TravelController {

    private final TravelService travelService;

    public TravelController(TravelService travelService) {
        this.travelService = travelService;
    }

    @Operation(
            summary = "Criação de nova viagem.",
            description = "Registra uma nova viagem no sistema para o motorista informado, consumindo a API do Mapbox para gerar o preview estático de distância, tempo estimado e cidade de destino.\n\n" +
                    "### Regras de Negócio:\n" +
                    "- **Validação de Estado:** O motorista deve estar ativamente cadastrado no sistema (`ACTIVE`).\n" +
                    "- **Trava de Simultaneidade:** O motorista **não pode** possuir nenhuma outra viagem em andamento ou agendada (status `PENDING` ou `TRAVELLING`). Caso possua, a criação será bloqueada.",
            tags = {"Travels"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Viagem criada com sucesso.",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TravelResponseDTO.class)),
                headers = @Header(name = "Location", description = "URI da viagem recém criada (ex.: /v1/travel/{id}",
                        schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida. Possíveis causas:\n" +
                            "- O corpo do JSON está malformado ou violou as regras de validação;\n" +
                            "- **Motorista Inativo:** O motorista informado está com status `INACTIVE` no sistema.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_DRIVER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Motorista não encontrado no banco de dados através do ID fornecido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "Motorista já possui outra viagem em andamento no sistema.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/create")
    public ResponseEntity<TravelResponseDTO> createTravel(@RequestBody TravelRequestDTO travelRequestDTO, UriComponentsBuilder componentsBuilder) {
        TravelResponseDTO responseDTO = travelService.createTravel(travelRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(responseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(responseDTO);
    }

    @PostMapping("/{travelId}/start")
    public ResponseEntity<Void> startTravel(@PathVariable UUID travelId) {
        travelService.startTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/end")
    public ResponseEntity<Void> endTravel(@PathVariable UUID travelId) {
        travelService.endTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/join")
    public ResponseEntity<Void> joinTravel(@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.joinTravel(travelId, studentEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/leave")
    public ResponseEntity<Void> leaveTravel (@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.leaveTravel(travelId, studentEmail, StudentTravelStatus.LEFT);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{travelId}/preview")
    public ResponseEntity<TravelPreviewDTO> getTravelPreview(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelService.getTravelPreview(travelId));
    }
}
