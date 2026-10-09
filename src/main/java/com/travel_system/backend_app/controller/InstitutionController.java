package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.InstitutionService;
import com.travel_system.backend_app.model.enums.InstitutionType;
import org.apache.coyote.Response;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.simpleframework.xml.Path;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequestMapping("/v1/institution")
@RestController
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<InstitutionResponseDTO>> getAllInstitutions(
            @RequestParam(required = false) String institutionName,
            @RequestParam(required = false)GeneralStatus status,
            @RequestParam(required = false) InstitutionType institutionType,
            @RequestParam(required = false) String course,
            @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok().body(institutionService.getAllInstitutions(institutionName, status, institutionType, course, pageable));
    }

    @GetMapping("/{institutionId}")
    public ResponseEntity<InstitutionResponseDTO> getInstitutionById(@PathVariable UUID institutionId) {
        return ResponseEntity.ok().body(institutionService.getInstitutionById(institutionId));
    }

    @GetMapping("/{studentId}/student")
    public ResponseEntity<List<InstitutionResponseDTO>> getInstitutionsByStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(institutionService.getInstitutionsByStudent(studentId));
    }

    @GetMapping("/{institutionId}/courses")
    public ResponseEntity<Page<InstitutionCourseResponseDTO>> getAllInstitutionCourses(@PathVariable UUID institutionId, @PageableDefault(size = 15) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(institutionService.getAllInstitutionCourses(institutionId, pageable));
    }

    @PostMapping("/new")
    public ResponseEntity<InstitutionResponseDTO> createInstitution(@Valid @RequestBody InstitutionRequestDTO dto, UriComponentsBuilder componentsBuilder) {
        InstitutionResponseDTO institution = institutionService.createInstitution(dto);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(institution.id()).toUri();

        return ResponseEntity.created(uri).body(institution);
    }

    @PatchMapping("/update/{institutionId}")
    public ResponseEntity<InstitutionResponseDTO> updateInstitution(@PathVariable UUID institutionId, @Valid @RequestBody InstitutionUpdateDTO dto) {
        return ResponseEntity.ok().body(institutionService.updateInstitution(institutionId, dto));
    }

    @PatchMapping("/{institutionId}/add-course")
    public ResponseEntity<InstitutionCourseResponseDTO> addCourse(@PathVariable UUID institutionId, @Valid @RequestBody InstitutionCourseRequestDTO dto) {
        return ResponseEntity.ok().body(institutionService.addCourse(institutionId, dto));
    }

    @PatchMapping("/{institutionId}/remove/{courseId}")
    public ResponseEntity<Void> removeCourse(@PathVariable UUID institutionId, @PathVariable UUID courseId) {
        institutionService.removeCourse(institutionId, courseId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{institutionId}/reactive/{courseId}")
    public ResponseEntity<Void> reactivateCourse(@PathVariable UUID institutionId, @PathVariable UUID courseId) {
        institutionService.reactivateCourse(institutionId, courseId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("update/{institutionId}/status")
    public ResponseEntity<Void> updateInstitutionStatus(@PathVariable UUID institutionId, @Valid @RequestBody UpdateStatusDTO dto) {
        institutionService.updateInstitutionStatus(institutionId, dto);

        return ResponseEntity.noContent().build();
    }

}
