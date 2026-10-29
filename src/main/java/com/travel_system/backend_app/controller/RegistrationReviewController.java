package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RejectRegistrationDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RequestPhotoResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationReviewDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationReviewDetailsDTO;
import com.travel_system.backend_app.service.RegistrationReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/registration/review")
public class RegistrationReviewController {

    private final RegistrationReviewService registrationReviewService;

    public RegistrationReviewController(RegistrationReviewService registrationReviewService) {
        this.registrationReviewService = registrationReviewService;
    }

    @GetMapping("/queue")
    public ResponseEntity<Page<TransportRegistrationReviewDTO>> reviewQueue(@PageableDefault(value = 15) Pageable pageable) {
        return ResponseEntity.ok().body(registrationReviewService.reviewQueue(pageable));
    }

    @GetMapping("/details/{requestId}")
    public ResponseEntity<TransportRegistrationReviewDetailsDTO> getRequestDetail(@PathVariable UUID requestId) {
        return ResponseEntity.ok().body(registrationReviewService.getRequestDetail(requestId));
    }

    @GetMapping("/download/{requestId}/document/{documentId}")
    public ResponseEntity<DocumentDownloadResponseDTO> downloadRequestDocument(@PathVariable UUID requestId, @PathVariable UUID documentId) {
        return ResponseEntity.accepted().body(registrationReviewService.downloadRequestDocument(requestId, documentId));
    }

    @GetMapping("/photo/{requestId}")
    public ResponseEntity<RequestPhotoResponseDTO> getRequestedPhoto(@PathVariable UUID requestId) {
        return ResponseEntity.ok().body(registrationReviewService.getRequestedPhoto(requestId));
    }

    @PutMapping("/approve/{requestId}")
    public ResponseEntity<TransportRegistrationReviewDetailsDTO> approve(@PathVariable UUID requestId) {
        return ResponseEntity.ok().body(registrationReviewService.approve(requestId));
    }

    @PatchMapping("/reject/{requestId}")
    public ResponseEntity<TransportRegistrationReviewDetailsDTO> reject(@PathVariable UUID requestId, @Valid @RequestBody RejectRegistrationDTO dto) {
        return ResponseEntity.ok().body(registrationReviewService.reject(requestId, dto));
    }


}
