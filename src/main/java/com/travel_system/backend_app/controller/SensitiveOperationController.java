package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.security.SensitiveOperationResponseDTO;
import com.travel_system.backend_app.model.dtos.security.SensitiveOperationReviewDTO;
import com.travel_system.backend_app.service.SensitiveOperationApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import org.simpleframework.xml.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/security/sensitive-operations")
public class SensitiveOperationController {

    private final SensitiveOperationApprovalService sensitiveOperationApprovalService;

    public SensitiveOperationController(SensitiveOperationApprovalService sensitiveOperationApprovalService) {
        this.sensitiveOperationApprovalService = sensitiveOperationApprovalService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<SensitiveOperationReviewDTO> review(@PathVariable String token) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.review(token));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<SensitiveOperationResponseDTO> approve(@PathVariable UUID id) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.approve(id));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<SensitiveOperationResponseDTO> execute(@PathVariable UUID id) {
        return ResponseEntity.ok().body(sensitiveOperationApprovalService.execute(id));
    }
}
