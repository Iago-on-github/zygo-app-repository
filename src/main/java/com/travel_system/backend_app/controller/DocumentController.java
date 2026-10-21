package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RateLimited;
import com.travel_system.backend_app.model.dtos.request.DocumentUploadRequest;
import com.travel_system.backend_app.model.dtos.response.DocumentDownloadResponseDTO;
import com.travel_system.backend_app.model.dtos.response.DocumentResponseDTO;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import com.travel_system.backend_app.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /*
    * endpoints para alunos e motoristas
    * */

    @GetMapping("/me")
    public ResponseEntity<List<DocumentResponseDTO>> myDocuments(@RequestParam List<DocumentStatus> statuses) {
        return ResponseEntity.ok().body(documentService.myDocuments(statuses));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping(value = "/me/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDTO> uploadMine(@Valid @ModelAttribute DocumentUploadRequest dto, @RequestParam("file") MultipartFile file, UriComponentsBuilder componentsBuilder) {
        DocumentResponseDTO documentResponseDTO = documentService.uploadMine(dto, file);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(documentResponseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(documentResponseDTO);
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @GetMapping("/me/{documentId}/download")
    public ResponseEntity<DocumentDownloadResponseDTO> downloadMine(@PathVariable UUID documentId) {
        return ResponseEntity.ok().body(documentService.downloadMine(documentId));
    }

    /*
     * endpoints para os responsáveis por alunos menores
     * */

    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<DocumentResponseDTO>> studentDocuments(@PathVariable UUID studentId, @RequestParam List<DocumentStatus> statuses) {
        return ResponseEntity.ok().body(documentService.studentDocuments(studentId, statuses));
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @PostMapping(value = "/students/{studentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponseDTO> uploadForStudent(@PathVariable UUID studentId, @Valid @ModelAttribute DocumentUploadRequest dto, @RequestParam("file") MultipartFile file, UriComponentsBuilder componentsBuilder) {
        DocumentResponseDTO documentResponseDTO = documentService.uploadForStudent(studentId, dto, file);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(documentResponseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(documentResponseDTO);
    }

    @RateLimited(RateLimitPolicy.SENSITIVE_OPERATION)
    @GetMapping("/students/{studentId}/{documentId}/download")
    public ResponseEntity<DocumentDownloadResponseDTO> downloadForStudent(@PathVariable UUID studentId, @PathVariable UUID documentId) {
        return ResponseEntity.ok().body(documentService.downloadForStudent(studentId, documentId));
    }
}
