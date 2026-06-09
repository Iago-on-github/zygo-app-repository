package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/drivers")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @Operation(
            summary = "Listar todos os Motoristas.",
            description = "Retorna uma List com todos os Motoristas cadastrados no sistema.",
            tags = {"Drivers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List contendo todos os Drivers retornada com sucesso.",
                content = @Content(mediaType = "application/json",
                        array = @ArraySchema(schema = @Schema(implementation = DriverResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
    })
    @GetMapping("/all")
    public ResponseEntity<List<DriverResponseDTO>> getAllDrivers() {
        return ResponseEntity.ok().body(driverService.getAllDrivers());
    }

    @Operation(
            summary = "Listar todos os Motoristas por Status.",
            description = "Retorna uma lista de motoristas cadastrados filtrada pelo status fornecido. " +
                    "**Nota importante:** Se nenhum status for enviado na requisição, o sistema assumirá por padrão o status 'ACTIVE'. " +
                    "Requer autenticação com o perfil de **DRIVER**.",
            tags = {"Drivers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List contendo todos os Drivers retornada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DriverResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão necessária para listar motoristas.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping()
    public ResponseEntity<List<DriverResponseDTO>> getDriversByStatus(@Parameter(description = "Status para filtragem dos motoristas. Se omitido, o padrão é 'ACTIVE'.", example = "ACTIVE")
                                                                          @RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(driverService.getDriversByStatus(status));
    }

    @Operation(
            summary = "Obter o Motorista Logado",
            description = "Retorna o atual Motorista logado. Requer autenticação com o perfil de DRIVER.",
            tags = {"Drivers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver logado retornado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DriverResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão DRIVER.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Entidade do Motorista não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/me")
    public ResponseEntity<DriverResponseDTO> getCurrentDriver(Authentication auth) {
        String email = auth.getName();

        DriverResponseDTO loggedDriver = driverService.getCurrentDriver(email);

        return ResponseEntity.ok().body(loggedDriver);
    }

    @PostMapping
    public ResponseEntity<DriverResponseDTO> createDriver(@Valid @RequestBody DriverRequestDTO driverRequestDTO, UriComponentsBuilder componentsBuilder) {
        DriverResponseDTO newDriver = driverService.createDriver(driverRequestDTO);
        
        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newDriver.id()).toUri();
        
        return ResponseEntity.created(uri).body(newDriver);
    }

    @PatchMapping("/me")
    public ResponseEntity<DriverResponseDTO> updateCurrentDriver(Authentication auth, @Valid @RequestBody DriverUpdateDTO driverUpdateDTO) {
        String email = auth.getName();
        DriverResponseDTO loggedDriver = driverService.updateCurrentDriver(email, driverUpdateDTO);
        return ResponseEntity.ok().body(loggedDriver);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateDriver(@PathVariable UUID id, @Valid @RequestBody UpdateEntityStatusDTO entityStatusDTO) {
        driverService.updateDriver(id, entityStatusDTO);
        return ResponseEntity.noContent().build();
    }
}
