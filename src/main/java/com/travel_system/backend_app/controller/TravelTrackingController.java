package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.service.TravelTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tracking")
public class TravelTrackingController {

    private final TravelTrackingService travelTrackingService;

    public TravelTrackingController(TravelTrackingService travelTrackingService) {
        this.travelTrackingService = travelTrackingService;
    }

    @Operation(
            summary = "Principal ingestor de GPS",
            description = "Principal consumidor de telemetria do motorista em alta frequência, determina se houve desvio de rota e necessidade de recalculo da rota internamente com base nas regras de negócio. \n" +
                    "### Regras de negócio: \n" +
                    "- **Estado da viagem**: O monitoramento só aceita pings se a viagem estiver atualmente em andamento `TRAVELLING`. \n" +
                    "- **Cache performance**: Persiste a posição atual do veículo (lat/lng, speed, heading), liberando a thread do mobile o mais rápido possível. \n" +
                    "- **Algoritmo de Recalculo**: O sistema possui um limite de tolerância de 50 metros. Se o veículo se mover mais do que isso com base no último cálculo armazenado no redis, o backend intercepta, chama o mapbox e faz o recalculo de rota. \n" +
                    "- **Eventos internos de domínio**: Dispara eventos internos que acionam o algoritmo assíncrono de verificação de proximidade dos alunos  (auto-desconexão se o estudante estiver muito longe por muito tempo) e encaminha a localização para as filas do broker para difusão via MQTT. \n",
            tags = {"Tracking"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "ping recebido e processado com sucesso, mobile liberado.",
                content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_DRIVER'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Viagem não encontrada no banco de dados",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "409", description = "A viagem não está registrada como em andamento (`TRAVELLING)`",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "502", description = "API do mapbox retornou com dados inválidos, insuficientes ou houve falhas na chamada.",
                    content = @Content(schema = @Schema(hidden = true))),

    })
    @PostMapping("/travels/{travelId}/locations/{cityId}")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> markDriverCheckpoint(@PathVariable UUID cityId, @PathVariable UUID travelId, @Valid @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/travels/{travelId}/students/{studentId}/embark")
    public ResponseEntity<Void> confirmStudentEmbark( @PathVariable UUID travelId, @PathVariable UUID studentId) {
        travelTrackingService.confirmEmbarkOnTravel(travelId, studentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/travels/{travelId}/location")
    public ResponseEntity<LiveLocationDTO> getDriverPosition(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getDriverPosition(travelId));
    }

    @GetMapping("/travels/{travelId}/history")
    public ResponseEntity<Page<LocationPointDTO>> getTravelHistory(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getTravelHistory(travelId));
    }
}
