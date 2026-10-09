package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.AdministratorUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.AdministratorResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.AdministratorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/admins")
public class AdministratorController {

    private final AdministratorService administratorService;

    public AdministratorController(AdministratorService administratorService) {
        this.administratorService = administratorService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<AdministratorResponseDTO>> getAllAdmins(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) String jobTitle,
            @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok().body(administratorService.getAllAdministrators(email, name, lastName, neighborhood, jobTitle, pageable));
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

    @PatchMapping("/status")
    public ResponseEntity<Void> updateAdministrator(@Valid @RequestBody UpdateStatusDTO administratorStatusDTO) {
        administratorService.updateAdministrator(administratorStatusDTO.status());
        return ResponseEntity.noContent().build();
    }
}
