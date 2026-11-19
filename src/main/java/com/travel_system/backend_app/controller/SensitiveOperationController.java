package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationReviewDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationTokenDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.SensitiveOperationApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/security/sensitive-operations")
@RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
@RequireAccess(AccessScope.UNRESTRICTED)
public class SensitiveOperationController {

    private final SensitiveOperationApprovalService sensitiveOperationApprovalService;

    public SensitiveOperationController(SensitiveOperationApprovalService sensitiveOperationApprovalService) {
        this.sensitiveOperationApprovalService = sensitiveOperationApprovalService;
    }

    @PostMapping("/review")
    public ResponseEntity<SensitiveOperationReviewDTO> review(@Valid @RequestBody SensitiveOperationTokenDTO dto) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.review(dto.token()));
    }

    @PostMapping("/approve")
    public ResponseEntity<SensitiveOperationResponseDTO> approve(@Valid @RequestBody SensitiveOperationTokenDTO dto) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.approve(dto.token()));
    }

    @PostMapping("/execute")
    public ResponseEntity<SensitiveOperationResponseDTO> execute(@Valid @RequestBody SensitiveOperationTokenDTO dto) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.execute(dto.token()));
    }

    @PostMapping("/reject")
    public ResponseEntity<SensitiveOperationResponseDTO> reject(@Valid @RequestBody SensitiveOperationTokenDTO dto) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.reject(dto.token()));
    }
}
