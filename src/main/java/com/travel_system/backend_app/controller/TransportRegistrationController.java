package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.SaveDraftDTO;
import com.travel_system.backend_app.model.dtos.response.TransportRegistrationResponseDTO;
import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.service.TransportRegistrationService;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transport/registration")
public class TransportRegistrationController {

    private final TransportRegistrationService transportRegistrationService;

    public TransportRegistrationController(TransportRegistrationService transportRegistrationService) {
        this.transportRegistrationService = transportRegistrationService;
    }

    /*
    * operações para estudantes e motoristas maiores de idade
    * */
    @GetMapping("/me")
    public ResponseEntity<TransportRegistrationResponseDTO> consultMine() {
        return ResponseEntity.ok().body(transportRegistrationService.consultMine());
    }

    @PostMapping("/me/start")
    public ResponseEntity<TransportRegistrationResponseDTO> startMineRegistration(UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startMineRegistration();

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PutMapping("/me/draft")
    public ResponseEntity<TransportRegistrationResponseDTO> saveMineDraft(@Valid @RequestBody SaveDraftDTO dto) {
        return ResponseEntity.ok().body(transportRegistrationService.saveMineDraft(dto));
    }

    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransportRegistrationResponseDTO> uploadDocument(MultipartFile file, @RequestParam("documentType") DocumentType documentType) {
        return ResponseEntity.ok().body(transportRegistrationService.uploadDocument(file, documentType));
    }

    @PostMapping(value = "me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransportRegistrationResponseDTO> uploadMinePhoto(MultipartFile file) {
        return ResponseEntity.ok().body(transportRegistrationService.uploadMinePhoto(file));
    }

    @PostMapping("/me/submit")
    public ResponseEntity<TransportRegistrationResponseDTO> submitMine() {
        return ResponseEntity.ok().body(transportRegistrationService.submitMine());
    }

    @PostMapping("/me/cancel")
    public ResponseEntity<TransportRegistrationResponseDTO> cancelMine() {
        return ResponseEntity.ok().body(transportRegistrationService.cancelMine());
    }

    @PostMapping("/me/renewal")
    public ResponseEntity<TransportRegistrationResponseDTO> startRenewalMine(UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startRenewalMine();

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PostMapping("/me/update")
    public ResponseEntity<TransportRegistrationResponseDTO> startUpdateMine(UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startUpdateMine();

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PostMapping("/me/resubmit")
    public ResponseEntity<TransportRegistrationResponseDTO> resubmitMine(UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.resubmitMine();

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    /*
    * resonsável de estudante menor
    * */

    @GetMapping("/students/{studentId}")
    public ResponseEntity<TransportRegistrationResponseDTO> consultForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(transportRegistrationService.consultForStudent(studentId));
    }

    @PostMapping("/students/{studentId}/start")
    public ResponseEntity<TransportRegistrationResponseDTO> startRegistrationForStudent(@PathVariable UUID studentId, UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startRegistrationForStudent(studentId);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PutMapping("/students/{studentId}/draft")
    public ResponseEntity<TransportRegistrationResponseDTO> saveDraftForStudent(@PathVariable UUID studentId, @Valid @RequestBody SaveDraftDTO dto) {
        return ResponseEntity.ok().body(transportRegistrationService.saveDraftForStudent(studentId, dto));
    }

    @PostMapping(value = "/students/{studentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransportRegistrationResponseDTO> uploadDocumentForStudent(@PathVariable UUID studentId, @RequestParam("documentType") DocumentType documentType, MultipartFile file) {
        return ResponseEntity.ok().body(transportRegistrationService.uploadDocumentForStudent(studentId, file, documentType));
    }

    @PostMapping(value = "/students/{studentId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TransportRegistrationResponseDTO> uploadStudentPhoto(@PathVariable UUID studentId, MultipartFile file) {
        return ResponseEntity.ok().body(transportRegistrationService.uploadStudentPhoto(studentId, file));
    }

    @PostMapping("/students/{studentId}/submit")
    public ResponseEntity<TransportRegistrationResponseDTO> submitForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(transportRegistrationService.submitForStudent(studentId));
    }

    @PostMapping("/students/{studentId}/cancel")
    public ResponseEntity<TransportRegistrationResponseDTO> cancelForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(transportRegistrationService.cancelForStudent(studentId));
    }

    @PostMapping("/students/{studentId}/renewal")
    public ResponseEntity<TransportRegistrationResponseDTO> startRenewalForStudent(@PathVariable UUID studentId, UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startRenewalForStudent(studentId);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PostMapping("/students/{studentId}/update")
    public ResponseEntity<TransportRegistrationResponseDTO> startUpdateForStudent(@PathVariable UUID studentId, UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.startUpdateForStudent(studentId);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PostMapping("/students/{studentId}/resubmit")
    public ResponseEntity<TransportRegistrationResponseDTO> resubmitForStudent(@PathVariable UUID studentId, UriComponentsBuilder componentsBuilder) {
        TransportRegistrationResponseDTO dto = transportRegistrationService.resubmitForStudent(studentId);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(dto.registrationId()).toUri();

        return ResponseEntity.created(uri).body(dto);
    }

}
