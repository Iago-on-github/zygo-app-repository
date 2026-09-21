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
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
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

    @GetMapping("/all")
    public ResponseEntity<Page<DriverResponseDTO>> getAllDrivers(@PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(driverService.getAllDrivers(pageable));
    }

    @GetMapping()
    public ResponseEntity<Page<DriverResponseDTO>> getDriversByStatus(@RequestParam(required = false) GeneralStatus status, @PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(driverService.getDriversByStatus(status,pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<DriverResponseDTO> getCurrentDriver() {
        DriverResponseDTO loggedDriver = driverService.getCurrentDriver();

        return ResponseEntity.ok().body(loggedDriver);
    }

    @PostMapping
    public ResponseEntity<DriverResponseDTO> createDriver(@Valid @RequestBody DriverRequestDTO driverRequestDTO, UriComponentsBuilder componentsBuilder) {
        DriverResponseDTO newDriver = driverService.createDriver(driverRequestDTO);
        
        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newDriver.id()).toUri();
        
        return ResponseEntity.created(uri).body(newDriver);
    }

    @PatchMapping("/me")
    public ResponseEntity<DriverResponseDTO> updateCurrentDriver(@Valid @RequestBody DriverUpdateDTO driverUpdateDTO) {
        DriverResponseDTO loggedDriver = driverService.updateCurrentDriver(driverUpdateDTO);

        return ResponseEntity.ok().body(loggedDriver);
    }

    @PatchMapping("/status")
    public ResponseEntity<Void> updateDriver(@Valid @RequestBody UpdateEntityStatusDTO entityStatusDTO) {
        driverService.updateDriver(entityStatusDTO);

        return ResponseEntity.noContent().build();
    }
}
