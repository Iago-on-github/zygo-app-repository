package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.CpfSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.ResponsibleAdultResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponsibleAdultDTO;
import com.travel_system.backend_app.service.ResponsibleAdultService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/responsibles")
public class ResponsibleAdultController {

    private final ResponsibleAdultService responsibleAdultService;

    public ResponsibleAdultController(ResponsibleAdultService responsibleAdultService) {
        this.responsibleAdultService = responsibleAdultService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ResponsibleAdultResponseDTO>> getAllResponsibleAdults(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String neighborhood,
            @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok().body(responsibleAdultService.getAllResponsibleAdults(email, name, lastName, neighborhood, pageable));
    }

    @GetMapping("/{responsibleAdultId}")
    public ResponseEntity<ResponsibleAdultResponseDTO> getResponsibleAdultById(@PathVariable UUID responsibleAdultId) {
        return ResponseEntity.ok().body(responsibleAdultService.getResponsibleAdultById(responsibleAdultId));
    }

    @GetMapping("/name")
    public ResponseEntity<Page<ResponsibleAdultResponseDTO>> getResponsibleAdultByName(@RequestParam String name, @PageableDefault(size = 15) @SortDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(responsibleAdultService.getResponsibleAdultByName(name, pageable));
    }

    @GetMapping("/search-by-cpf")
    public ResponseEntity<ResponsibleAdultResponseDTO> getResponsibleAdultByCpf(@Valid @RequestBody CpfSearchRequestDTO dto) {
        return ResponseEntity.ok().body(responsibleAdultService.getResponsibleAdultByCpf(dto));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<ResponsibleAdultResponseDTO> getResponsibleAdultByStudentId(@PathVariable UUID studentId) {
        return ResponseEntity.ok().body(responsibleAdultService.getResponsibleAdultByStudentId(studentId));
    }

    @GetMapping("/students/{responsibleAdultId}")
    public ResponseEntity<Set<StudentResponsibleAdultDTO>> getStudentsByResponsibleAdult(@PathVariable UUID responsibleAdultId) {
        return ResponseEntity.ok().body(responsibleAdultService.getStudentsByResponsibleAdult(responsibleAdultId));
    }

    @GetMapping("/me")
    public ResponseEntity<ResponsibleAdultResponseDTO> getCurrentResponsibleAdult() {
        return ResponseEntity.ok().body(responsibleAdultService.getCurrentResponsibleAdult());
    }

    @PatchMapping("/update")
    public ResponseEntity<ResponsibleAdultResponseDTO> updateResponsibleAdult(@Valid @RequestBody ResponsibleAdultUpdateDTO dto) {
        return ResponseEntity.ok().body(responsibleAdultService.updateResponsibleAdult(dto));
    }

    @PatchMapping("/status")
    public ResponseEntity<ResponsibleAdultResponseDTO> updateResponsibleAdultStatus(@Valid @RequestBody UpdateStatusDTO dto) {
        responsibleAdultService.updateResponsibleAdultStatus(dto);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/student/add")
    public ResponseEntity<Void> addStudentsToResponsibleAdult(@Valid @RequestBody StudentResponsibleAdultDTO dto) {
        responsibleAdultService.addStudentsToResponsibleAdult(dto);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{newResponsibleAdultId}/transfer/{studentIdToTransfer}")
    public ResponseEntity<Void> transferStudentToAnotherResponsible(@PathVariable UUID newResponsibleAdultId, @PathVariable UUID studentIdToTransfer) {
        responsibleAdultService.transferStudentToAnotherResponsible(newResponsibleAdultId, studentIdToTransfer);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/student/{studentId}/remove")
    public ResponseEntity<Void> removeStudent(@PathVariable UUID studentId) {
        responsibleAdultService.removeStudent(studentId);

        return ResponseEntity.ok().build();
    }
}
