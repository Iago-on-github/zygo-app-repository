package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.DriverRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/drivers/api")
public class DriverController {
    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public ResponseEntity<List<DriverResponseDTO>> getAllDrivers() {
        return ResponseEntity.ok().body(driverService.getAllDrivers());
    }

    @GetMapping("/active")
    public ResponseEntity<List<DriverResponseDTO>> getAllActiveDrivers() {
        return ResponseEntity.ok().body(driverService.getAllActiveDrivers());
    }

    @GetMapping("/inactive")
    public ResponseEntity<List<DriverResponseDTO>> getAllInactiveDrivers() {
        return ResponseEntity.ok().body(driverService.getAllInactiveDrivers());
    }

    @GetMapping("/logged")
    public ResponseEntity<DriverResponseDTO> getLoggedInDriverProfile(Authentication auth) {
        String email = auth.getName();
        DriverResponseDTO loggedDriver = driverService.getLoggedInDriverProfile(email);
        return ResponseEntity.ok().body(loggedDriver);
    }

    @PostMapping
    public ResponseEntity<DriverResponseDTO> createDriver(@RequestBody DriverRequestDTO driverRequestDTO, UriComponentsBuilder componentsBuilder) {
        DriverResponseDTO newDriver = driverService.createDriver(driverRequestDTO);
        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newDriver.id()).toUri();
        return ResponseEntity.created(uri).body(newDriver);
    }

    @PutMapping("/logged")
    public ResponseEntity<DriverResponseDTO> updateLoggedDriver(Authentication auth, @RequestBody DriverRequestDTO driverRequestDTO) {
        String email = auth.getName();
        DriverResponseDTO loggedDriver = driverService.updateLoggedDriver(email, driverRequestDTO);
        return ResponseEntity.ok().body(loggedDriver);
    }

    @PutMapping("/disable/{id}")
    public ResponseEntity<Void> disableDriver(@PathVariable UUID id) {
        driverService.disableDriver(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/enable/{id}")
    public ResponseEntity<Void> enableUser(@PathVariable UUID id) {
        driverService.enableDriver(id);
        return ResponseEntity.noContent().build();
    }
}
