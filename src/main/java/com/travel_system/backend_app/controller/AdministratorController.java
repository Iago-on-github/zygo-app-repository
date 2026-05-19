package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateAdministratorStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.AdministratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admins")
public class AdministratorController {

    private final AdministratorService administratorService;

    public AdministratorController(AdministratorService administratorService) {
        this.administratorService = administratorService;
    }

    @GetMapping
    public ResponseEntity<List<AdministratorResponseDTO>> getAllAdmins() {
        return ResponseEntity.ok().body(administratorService.getAllAdministrators());
    }

    @GetMapping
    public ResponseEntity<List<AdministratorResponseDTO>> getAdminsByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(administratorService.getAllAdministratorsByStatus(status));
    }

    @GetMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> getCurrentAdministrator(Authentication auth) {
        String authEmail = auth.getName();
        return ResponseEntity.ok().body(administratorService.getCurrentAdministrator(authEmail));
    }

    @PostMapping
    public ResponseEntity<AdministratorResponseDTO> createAdministrator(@RequestBody AdministratorRequestDTO admRequestDTO, UriComponentsBuilder componentsBuilder) {
        AdministratorResponseDTO newAdm = administratorService.createAdministrator(admRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newAdm.id()).toUri();

        return ResponseEntity.created(uri).body(newAdm);
    }

    @PatchMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> updateCurrentAdministrator(@RequestBody AdministratorUpdateDTO administratorUpdateDto, Authentication auth) {
        String authEmail = auth.getName();
        return ResponseEntity.ok().body(administratorService.updateCurrentAdministrator(authEmail, administratorUpdateDto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateAdministrator(@PathVariable UUID id, @RequestBody UpdateAdministratorStatusDTO administratorStatusDTO) {
        administratorService.updateAdministrator(id, administratorStatusDTO.status());
        return ResponseEntity.noContent().build();
    }

//    REALIZAR O TRATAMENTO DE EXCEÇÕES - QUANDO NÃO ESTÁ LOGANDO ELE RETORNA 403
//    TOKEN EXPIRANDO MT RAPIDO
}
