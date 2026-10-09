package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.CpfSearchRequestDTO;
import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultLinkRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateStatusDTO;
import com.travel_system.backend_app.model.dtos.response.InstitutionResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.service.InstitutionService;
import com.travel_system.backend_app.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/students")
public class StudentController {

    private final InstitutionService institutionService;
    private final StudentService studentService;

    public StudentController(InstitutionService institutionService, StudentService studentService) {
        this.institutionService = institutionService;
        this.studentService = studentService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<StudentResponseDTO>> getAllStudents(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String neighborhood,
            @RequestParam(required = false) String institutionName,
            @RequestParam(required = false) InstitutionType institutionType,
            @RequestParam(required = false) Shift shift,
            @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok().body(studentService.getAllStudents(email, name, lastName, neighborhood, institutionName, institutionType, shift, pageable));
    }

    @GetMapping("/institutions/{institutionId}")
    public ResponseEntity<Page<StudentResponseDTO>> getAllStudentsByInstitution(@PathVariable UUID institutionId, @RequestParam(required = false) Set<UUID> courseIds, @PageableDefault(size = 15) Pageable pageable) {

        return ResponseEntity.ok().body(studentService.getAllStudentsByInstitution(institutionId, courseIds, pageable));
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<StudentResponseDTO> getStudentById(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studentService.getStudentById(studentId));
    }

    @PostMapping("/search-by-cpf")
    public ResponseEntity<StudentResponseDTO> getStudentByCpf(@Valid @RequestBody CpfSearchRequestDTO dto) {
        return ResponseEntity.ok(studentService.getStudentByCpf(dto.cpf()));
    }

    @GetMapping
    public ResponseEntity<Page<StudentResponseDTO>> getStudentsByStatus(@RequestParam(required = false) GeneralStatus status, @PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(studentService.getStudentsByStatus(status, pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<StudentResponseDTO> getCurrentStudent() {
        return ResponseEntity.ok().body(studentService.getCurrentStudent());
    }

    @PatchMapping("/add/responsible")
    public ResponseEntity<Void> addResponsibleAdult(@Valid @RequestBody ResponsibleAdultLinkRequestDTO dto) {
        studentService.addResponsibleAdult(dto);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me")
    public ResponseEntity<StudentResponseDTO> updateCurrentStudent(@Valid @RequestBody StudentUpdateDTO studentUpdateDTO) {
        return ResponseEntity.ok().body(studentService.updateCurrentStudent(studentUpdateDTO));
    }

    @PatchMapping("/remove/responsible")
    public ResponseEntity<Void> removeResponsibleAdult() {
        studentService.removeResponsibleAdult();

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/status")
    public ResponseEntity<Void> updateStudentStatus(@Valid @RequestBody UpdateStatusDTO newStudentStatus) {
        studentService.updateStudentStatus(newStudentStatus.status());

        return ResponseEntity.noContent().build();
    }

}
