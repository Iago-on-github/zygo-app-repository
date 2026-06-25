package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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

    @Operation(
            summary = "Criar um novo Customer",
            description = "Cadastra um novo Customer no sistema e valida duplicidade de dados. Pode ser criado apenas por Platform Administrators",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer criado com sucesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdministratorResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI do Customer criado (ex: /v1/customers/{id})", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- **Campos obrigatórios** inválidos ou não preenchidos devidamente;\n" +
                    "- **CNPJ** já cadastrado no sistema;\n" +
                    "- **Permissão 'ROLE_PLATFORM_ADMIN'** não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_PLATFORM_ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Entidade City não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping
    public ResponseEntity<CustomerResponseDTO> createCustomer(@Valid @RequestBody CustomerRequestDTO customerRequestDTO, UriComponentsBuilder componentsBuilder) {
        CustomerResponseDTO customer = customerService.createCustomer(customerRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(customer.id()).toUri();

        return ResponseEntity.created(uri).body(customer);
    }

    @Operation(
            summary = "Atualiza o Customer",
            description = "Deve atualizar os campos de name ou profilePicture do Customer. Requer, obrigatoriamente, autenticação com perfil de Platform Administrator.",
            tags = {"Customers"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer atualizado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "Customer está desativado no sistema",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão 'ROLE_PLATFORM_ADMIN'.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Customer não encontrado no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> updateCustomer(@PathVariable UUID id, @RequestBody CustomerUpdateDTO customerUpdateDTO) {
        return ResponseEntity.ok().body(customerService.updateCustomer(id, customerUpdateDTO));
    }

    @Operation(
            summary = "Atualiza o status do Customer",
            description = "Deve atualizar o status do Customer, que controla a sua atividade/inatividade. Requer, obrigatoriamente, autenticação com perfil de Platform Administrator.",
            tags = {"Administrators"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Status do Customer modificado com sucesso. Nenhuma resposta é retornada no body.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "400", description = "Customer já possui esse status",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "403", description = "Não autorizado. O usuário autenticado não possui a permissão ADMIN.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Customer não encontrado no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Void> updateCustomerActive(@PathVariable UUID id, @RequestParam boolean isEnabled) {
        customerService.updateCustomerActive(id, isEnabled);
        return ResponseEntity.noContent().build();
    }
}
