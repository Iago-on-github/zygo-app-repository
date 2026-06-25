package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.coyote.Response;
import org.hibernate.annotations.Array;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Operation(
            summary = "Recupera todos os Customers",
            description = "Recupera todos os Customers cadastrados no sistema. Requer autorização como Administrador de Plataforma.",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customers retornados com sucesso",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CustomerResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O Usuário autenticado não possui a role 'ROLE_PLATFORM_ADMIN'. ",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/all")
    public ResponseEntity<Page<CustomerResponseDTO>> getAllCustomers() {
        return ResponseEntity.ok().body(customerService.getAllCustomers());
    }

    @Operation(
            summary = "Recupera um Customer pelo ID",
            description = "Recupera um Customer específico com base no ID. Requer autorização como Administrador de Plataforma.",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer retornado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Entidade não encontrada no banco de dados",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O Usuário autenticado não possui a role 'ROLE_PLATFORM_ADMIN'. ",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> findCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok().body(customerService.findCustomerById(id));
    }

    @Operation(
            summary = "Recupera um Customer pelo Slug",
            description = "Recupera um Customer específico com base no SLUG. Requer autorização como Administrador de Plataforma.",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer retornado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CustomerResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Entidade não encontrada no banco de dados",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O Usuário autenticado não possui a role 'ROLE_PLATFORM_ADMIN'. ",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/slug")
    public ResponseEntity<CustomerResponseDTO> findCustomerBySlug(@RequestParam String slug) {
        return ResponseEntity.ok().body(customerService.findCustomerBySlug(slug));
    }

    @Operation(
            summary = "Recupera todos os Customers pelo Status",
            description = "Recupera todos os Customers com base no boolean Status (ativo | inativo). Requer autorização como Administrador de Plataforma. " +
                    "Caso não seja fornecido o status, ele irá filtar com base no status ativo por padrão.",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customers retornados com sucesso",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CustomerResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O Usuário autenticado não possui a role 'ROLE_PLATFORM_ADMIN'. ",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping
    public ResponseEntity<List<CustomerResponseDTO>> findAllByActive(@RequestParam(required = false) boolean enabled) {
        return ResponseEntity.ok().body(customerService.findAllByActive(enabled));
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDTO> createCustomer(@Valid @RequestBody CustomerRequestDTO customerRequestDTO, UriComponentsBuilder componentsBuilder) {
        CustomerResponseDTO customer = customerService.createCustomer(customerRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(customer.id()).toUri();

        return ResponseEntity.created(uri).body(customer);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> updateCustomer(@PathVariable UUID id, @RequestBody CustomerUpdateDTO customerUpdateDTO) {
        return ResponseEntity.ok().body(customerService.updateCustomer(id, customerUpdateDTO));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Void> updateCustomerActive(@PathVariable UUID id, @RequestParam boolean isEnabled) {
        customerService.updateCustomerActive(id, isEnabled);
        return ResponseEntity.noContent().build();
    }
}
