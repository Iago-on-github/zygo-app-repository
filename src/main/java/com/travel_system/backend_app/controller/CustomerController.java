package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CustomerRequestDTO;
import com.travel_system.backend_app.model.dtos.request.CustomerUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.CustomerResponseDTO;
import com.travel_system.backend_app.service.CustomerService;
import org.apache.coyote.Response;
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

    @GetMapping("/all")
    public ResponseEntity<Page<CustomerResponseDTO>> getAllCustomers() {
        return ResponseEntity.ok().body(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> findCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok().body(customerService.findCustomerById(id));
    }

    @GetMapping("/slug")
    public ResponseEntity<CustomerResponseDTO> findCustomerBySlug(@RequestParam String slug) {
        return ResponseEntity.ok().body(customerService.findCustomerBySlug(slug));
    }

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
