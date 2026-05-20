package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.request.DriverUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.DriverService;
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

    @GetMapping("/all")
    public ResponseEntity<List<DriverResponseDTO>> getAllDrivers() {
        return ResponseEntity.ok().body(driverService.getAllDrivers());
    }

    @GetMapping()
    public ResponseEntity<List<DriverResponseDTO>> getDriversByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(driverService.getDriversByStatus(status));
    }

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
