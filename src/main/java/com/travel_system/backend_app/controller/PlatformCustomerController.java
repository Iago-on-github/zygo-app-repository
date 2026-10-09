package com.travel_system.backend_app.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.request.ChangeCustomerPlanPayload;
import com.travel_system.backend_app.model.dtos.request.CustomerOperationDataRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerOperationDataResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

//@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RestController
@RequestMapping("/v1/platform/customers")
public class PlatformCustomerController {

    private final CustomerService customerService;

    public PlatformCustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<CustomerOperationDataResponseDTO>> getAllCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String contactEmail,
            @RequestParam(required = false) String contactTelephone,
            @RequestParam(required = false) GeneralStatus status,
            @RequestParam(required = false) ClientSector clientSector,
            @RequestParam(required = false) CustomerPlan plan,
            @PageableDefault(value = 15) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok().body(customerService.getAllCustomers(name, cnpj, contactEmail, contactTelephone, status, clientSector, plan, pageable));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerOperationDataResponseDTO> findById(@PathVariable UUID customerId) {
        return ResponseEntity.ok().body(customerService.findById(customerId));
    }

    @GetMapping("/{slug}/slug")
    public ResponseEntity<CustomerOperationDataResponseDTO> findBySlug(@PathVariable String slug) {
        return ResponseEntity.ok().body(customerService.findBySlug(slug));
    }

    @PostMapping("/new")
    public ResponseEntity<CustomerOperationDataResponseDTO> createCustomer(@Valid @RequestBody CustomerOperationDataRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        CustomerOperationDataResponseDTO customer = customerService.createCustomer(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(customer.id()).toUri();

        return ResponseEntity.created(uri).body(customer);
    }

    @PatchMapping("/{customerId}/update")
    public ResponseEntity<CustomerOperationDataResponseDTO> updateCustomer(@PathVariable UUID customerId, @Valid @RequestBody CustomerUpdateDTO dto) {
        return ResponseEntity.ok().body(customerService.updateCustomer(customerId, dto));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PatchMapping("/plan/update")
    public ResponseEntity<SensitiveOperationResponseDTO> updateCustomerPlan(@Valid @RequestBody ChangeCustomerPlanPayload payload) {
        return ResponseEntity.ok().body(customerService.updateCustomerPlan(payload));
    }

    @PatchMapping("/{customerId}/delete")
    public ResponseEntity<Void> updateCustomerStatus(@PathVariable UUID customerId, @Valid @RequestBody UpdateStatusDTO dto) {
        customerService.updateCustomerStatus(customerId, dto);

        return ResponseEntity.noContent().build();
    }
}
