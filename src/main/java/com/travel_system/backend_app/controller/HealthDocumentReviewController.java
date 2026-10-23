package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.request.DocumentRejectDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.HealthDocumentReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/documents/review")
public class HealthDocumentReviewController {

    private final HealthDocumentReviewService healthDocumentReviewService;

    public HealthDocumentReviewController(HealthDocumentReviewService healthDocumentReviewService) {
        this.healthDocumentReviewService = healthDocumentReviewService;
    }

    @GetMapping("/review-queue")
    public ResponseEntity<Page<DocumentResponseDTO>> listPendingDocuments(@PageableDefault(value = 15) @SortDefault(direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(healthDocumentReviewService.listPendingDocuments(pageable));
    }

    @GetMapping("/{ownerId}")
    public ResponseEntity<List<DocumentResponseDTO>> findDocumentsByOwner(@PathVariable UUID ownerId) {
        return ResponseEntity.ok().body(healthDocumentReviewService.findDocumentsByOwner(ownerId));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @GetMapping("/{documentId}/download")
    public ResponseEntity<DocumentDownloadResponseDTO> downloadDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok().body(healthDocumentReviewService.downloadDocument(documentId));
    }

    @PostMapping("/{documentId}/approve")
    public ResponseEntity<Void> approveDocument(@PathVariable UUID documentId) {
        healthDocumentReviewService.approveDocument(documentId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{documentId}/reject")
    public ResponseEntity<Void> rejectDocument(@PathVariable UUID documentId, @Valid @RequestBody DocumentRejectDTO dto) {
        healthDocumentReviewService.rejectDocument(documentId, dto);

        return ResponseEntity.noContent().build();
    }
}
