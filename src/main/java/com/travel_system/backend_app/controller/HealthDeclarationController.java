package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.request.HealthConditionRequestDTO;
import com.travel_system.backend_app.model.dtos.request.HealthConditionUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.HealthDeclarationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.HealthDeclarationUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.HealthDeclarationResponseDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.HealthDeclarationService;
import jakarta.validation.Valid;
import org.simpleframework.xml.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/declarations")
public class HealthDeclarationController {

    private final HealthDeclarationService healthDeclarationService;

    public HealthDeclarationController(HealthDeclarationService healthDeclarationService) {
        this.healthDeclarationService = healthDeclarationService;
    }

    /*
    * endopoints para alunos e motoristas
    * */

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping("/me/declare")
    public ResponseEntity<HealthDeclarationResponseDTO> myDeclaration(@Valid @RequestBody HealthDeclarationRequestDTO dto) {
        return ResponseEntity.ok().body(healthDeclarationService.myDeclaration(dto));
    }

    @GetMapping("/me")
    public ResponseEntity<HealthDeclarationResponseDTO> getMyCurrentDeclaration() {
        return ResponseEntity.ok().body(healthDeclarationService.getMyCurrentDeclaration());
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PatchMapping("/me/{conditionId}/update")
    public ResponseEntity<HealthDeclarationResponseDTO> updateMyDeclaration(@PathVariable UUID conditionId, @Valid @RequestBody HealthConditionUpdateDTO dto ) {
        return ResponseEntity.ok().body(healthDeclarationService.updateMyDeclaration(conditionId, dto));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PutMapping("/me/conditions/add")
    public ResponseEntity<HealthDeclarationResponseDTO> addConditions(@Valid @RequestBody HealthConditionRequestDTO dto ) {
        return ResponseEntity.ok().body(healthDeclarationService.addCondition(dto));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PatchMapping("/me/confirm")
    public ResponseEntity<HealthDeclarationResponseDTO> confirmUnchangedMine() {
        return ResponseEntity.ok().body(healthDeclarationService.confirmUnchangedMine());
    }

    @PatchMapping("/me/conditions/{conditionId}/remove")
    public ResponseEntity<HealthDeclarationResponseDTO> removeCondition(@PathVariable UUID conditionId) {
        return ResponseEntity.ok().body(healthDeclarationService.removeCondition(conditionId));
    }

    /*
    * endpoints para alunos menores de idade e responsáveis operando com eles
    * */
    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping("/students/{studentId}/declare")
    public ResponseEntity<HealthDeclarationResponseDTO> minorStudentDeclaration(@PathVariable UUID studentId, @Valid @RequestBody HealthDeclarationRequestDTO dto) {
        return ResponseEntity.ok().body(healthDeclarationService.minorStudentDeclaration(studentId, dto));
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<HealthDeclarationResponseDTO> getMinorStudentDeclaration(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(healthDeclarationService.getMinorStudentDeclaration(studentId));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PatchMapping("/students/{studentId}/update/{conditionId}")
    public ResponseEntity<HealthDeclarationResponseDTO> updateMinorStudentDeclaration(@PathVariable UUID studentId, @PathVariable UUID conditionId, @Valid @RequestBody HealthConditionUpdateDTO dto ) {
        return ResponseEntity.ok().body(healthDeclarationService.updateMinorStudentDeclaration(studentId, conditionId, dto));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PutMapping("/students/{studentId}/add")
    public ResponseEntity<HealthDeclarationResponseDTO> addMinorStudentCondition(@PathVariable UUID studentId, @Valid @RequestBody HealthConditionRequestDTO dto) {
        return ResponseEntity.ok().body(healthDeclarationService.minorStudentAddCondition(studentId, dto));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PatchMapping("/students/{studentId}/confirm")
    public ResponseEntity<HealthDeclarationResponseDTO> confirmUnchangedForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(healthDeclarationService.confirmUnchangedForStudent(studentId));
    }

    @PatchMapping("/students/{studentId}/remove/{conditionId}")
    public ResponseEntity<HealthDeclarationResponseDTO> minorStudentRemoveCondition(@PathVariable UUID studentId, @PathVariable UUID conditionId) {
        return ResponseEntity.ok().body(healthDeclarationService.minorStudentRemoveCondition(studentId, conditionId));
    }

    // getDeclarationById (admin)
    @GetMapping("/{healthDeclarationId}")
    public ResponseEntity<HealthDeclarationResponseDTO> getDeclarationById(@PathVariable UUID healthDeclarationId) {
        return ResponseEntity.ok().body(healthDeclarationService.getDeclarationById(healthDeclarationId));
    }
}
