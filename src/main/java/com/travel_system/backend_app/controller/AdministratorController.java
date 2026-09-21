package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.AdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.AdministratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
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

    @GetMapping("/all")
    public ResponseEntity<Page<AdministratorResponseDTO>> getAllAdmins(@PageableDefault(size = 15) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(administratorService.getAllAdministrators(pageable));
    }


    @GetMapping
    public ResponseEntity<Page<AdministratorResponseDTO>> getAdminsByStatus(@PageableDefault(size = 15) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable, @RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(administratorService.getAllAdministratorsByStatus(status, pageable));
    }


    @GetMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> getCurrentAdministrator() {
        return ResponseEntity.ok().body(administratorService.getCurrentAdministrator());
    }


    @PatchMapping("/me")
    public ResponseEntity<AdministratorResponseDTO> updateCurrentAdministrator(@Valid @RequestBody AdministratorUpdateDTO administratorUpdateDto) {
        return ResponseEntity.ok().body(administratorService.updateCurrentAdministrator(administratorUpdateDto));
    }


    @PostMapping
    public ResponseEntity<AdministratorResponseDTO> createAdministrator(@Valid @RequestBody AdministratorRequestDTO admRequestDTO, UriComponentsBuilder componentsBuilder) {
        AdministratorResponseDTO newAdm = administratorService.createAdministrator(admRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newAdm.id()).toUri();

        return ResponseEntity.created(uri).body(newAdm);
    }


    @PatchMapping("/status")
    public ResponseEntity<Void> updateAdministrator(@Valid @RequestBody UpdateEntityStatusDTO administratorStatusDTO) {
        administratorService.updateAdministrator(administratorStatusDTO.status());
        return ResponseEntity.noContent().build();
    }
}
