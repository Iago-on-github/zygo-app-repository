package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.ResponsibleAdultLinkRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/students")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<StudentResponseDTO>> getAllStudents(@PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(studentService.getAllStudents(pageable));
    }

    @GetMapping
    public ResponseEntity<Page<StudentResponseDTO>> getStudentsByStatus(@RequestParam(required = false) GeneralStatus status, @PageableDefault(size = 15) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(studentService.getStudentsByStatus(status, pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<StudentResponseDTO> getCurrentStudent() {
        return ResponseEntity.ok().body(studentService.getCurrentStudent());
    }

    @PostMapping("/new")
    public ResponseEntity<StudentResponseDTO> createStudent(@Valid @RequestBody StudentRequestDTO studentRequestDTO, UriComponentsBuilder componentsBuilder) {
        StudentResponseDTO student = studentService.createStudent(studentRequestDTO);

        URI uri = componentsBuilder.path("{/id}").buildAndExpand(student.id()).toUri();

        return ResponseEntity.created(uri).body(student);
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
    public ResponseEntity<Void> updateStudentStatus(@Valid @RequestBody UpdateEntityStatusDTO newStudentStatus) {
        studentService.updateStudentStatus(newStudentStatus.status());

        return ResponseEntity.noContent().build();
    }

}
