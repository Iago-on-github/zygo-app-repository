package com.travel_system.backend_app.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_system.backend_app.model.dtos.request.PlatformAdministratorRequestDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.service.PlatformAdministratorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/internal/platform-admin")
public class PlatformAdministratorController {
    private final PlatformAdministratorService platformAdministratorService;

    public PlatformAdministratorController(PlatformAdministratorService platformAdministratorService) {
        this.platformAdministratorService = platformAdministratorService;
    }

    @PostMapping("/new")
    public ResponseEntity<SensitiveOperationResponseDTO> createPlatformAdm(@Valid @RequestBody PlatformAdministratorRequestDTO platformAdministratorRequestDTO) throws JsonProcessingException {
        return ResponseEntity.accepted().body(platformAdministratorService.createPlatformAdm(platformAdministratorRequestDTO));
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<SensitiveOperationResponseDTO> createFirstPlatformAdministrator(@Valid @RequestBody PlatformAdministratorRequestDTO platformAdministratorRequestDTO, HttpServletRequest request) throws JsonProcessingException {
        return ResponseEntity.accepted().body(platformAdministratorService.createFirstPlatformAdministrator(platformAdministratorRequestDTO, request));
    }
}
