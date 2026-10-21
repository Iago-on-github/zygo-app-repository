package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.request.HealthConsentRequestDTO;
import com.travel_system.backend_app.model.dtos.response.HealthConsentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.HealthConsentTermDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.HealthConsentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/health/consent")
public class HealthConsentController {

    private final HealthConsentService healthConsentService;

    public HealthConsentController(HealthConsentService healthConsentService) {
        this.healthConsentService = healthConsentService;
    }

    /*
    * endpoints de alunos maiores de idade e motoristas
    * */

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @GetMapping("/me/term")
    public ResponseEntity<HealthConsentTermDTO> getCurrentTerm() {
        return ResponseEntity.ok().body(healthConsentService.getCurrentTerm());
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping("/me/register")
    public ResponseEntity<HealthConsentResponseDTO> registerMyConsent(@Valid @RequestBody HealthConsentRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        HealthConsentResponseDTO healthConsentResponseDTO = healthConsentService.registerMyConsent(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(healthConsentResponseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(healthConsentResponseDTO);
    }

    @GetMapping("/me")
    public ResponseEntity<HealthConsentResponseDTO> getMyConsent() {
        return ResponseEntity.ok().body(healthConsentService.getMyConsent());
    }

    @PostMapping("/me/revoke")
    public ResponseEntity<Void> revokeMyConsent() {
        healthConsentService.revokeMyConsent();

        return ResponseEntity.noContent().build();
    }

    /*
     * endpoints de alunos menores de idade e responsáveis atuando com eles
     * */

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping("/students/{studentId}/register")
    public ResponseEntity<HealthConsentResponseDTO> registerMinorStudentConsent(@PathVariable UUID studentId, @Valid @RequestBody HealthConsentRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        HealthConsentResponseDTO healthConsentResponseDTO = healthConsentService.registerMinorStudentConsent(studentId, dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(healthConsentResponseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(healthConsentResponseDTO);
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<HealthConsentResponseDTO> getMinorStudentConsent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(healthConsentService.getMinorStudentConsent(studentId));
    }

    @PostMapping("/students/{studentId}/revoke")
    public ResponseEntity<Void> revokeMinorStudentConsent(@PathVariable UUID studentId) {
        healthConsentService.revokeMinorStudentConsent(studentId);

        return ResponseEntity.noContent().build();
    }

}
