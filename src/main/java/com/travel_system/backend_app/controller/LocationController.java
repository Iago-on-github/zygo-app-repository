package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/location")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(
            summary = "Atualizar posição geográfica do estudante",
            description = "Recebe as coordenadas de GPS do celular do estudante em tempo real e gerencia a atualização de sua posição no sistema, com monitoriamento de deslocamento.\n\n" +
                    "### Notas Importantes:\n" +
                    "- **Sem body (Void):** O servidor responde com HTTP 204. Não espere um JSON no corpo da resposta.\n" +
                    "- **Confirmação de Envio:** O front-end deve monitorar o status **204 No Content** para confirmar o sucesso do recebimento.\n" +
                    "- **Validação de Entrada:** O formato do JSON deve ser estritamente respeitado. Se dados cruciais (latitude/longitude) forem enviados como `null`, o backend irá ignorar o processamento silenciosamente e ainda assim responderá 204.\n" +
                    "- **Monitoriamento de deslocamento:** Para evitar sobrecarga no banco de dados, o sistema calcula a distância em metros entre o ping atual e o anterior:\n" +
                    "  - **Moveu mais de 3 metros?** O sistema atualiza a posição e o timestamp no banco de dados.\n" +
                    "  - **Moveu menos de 3 metros (ou não se moveu)?** O sistema ignora a gravação, mas mantém o retorno 204.",
            tags = {"StudentLocation"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Coordenadas processadas com sucesso. Nenhuma resposta é retornada no corpo.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Vínculo de viagem do estudante (StudentTravel) não encontrado com o ID fornecido.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping("/{studentTravelId}")
    public ResponseEntity<Void> studentPosition(@PathVariable UUID studentTravelId, @RequestBody LiveCoordinates coordinates) {
        locationService.updateStudentPosition(studentTravelId, coordinates);
        return ResponseEntity.noContent().build();
    }
}
