package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.security.SetupAuthProcessDTO;
import com.travel_system.backend_app.service.SetupAuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/set-up")
public class SetupAuthenticationController {

    private final SetupAuthenticationService setupAuthenticationService;

    public SetupAuthenticationController(SetupAuthenticationService setupAuthenticationService) {
        this.setupAuthenticationService = setupAuthenticationService;
    }

    @PostMapping
    public ResponseEntity<Void> setUpAuthentication(@RequestBody SetupAuthProcessDTO authPasswordDTO) {
        setupAuthenticationService.authenticateSensitiveOperation(authPasswordDTO.password());

        return ResponseEntity.noContent().build();
    }
}
