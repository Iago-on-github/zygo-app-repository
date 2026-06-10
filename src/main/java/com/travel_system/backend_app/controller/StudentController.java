package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.UpdateEntityStatusDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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

    @Operation(
            summary = "Listar todos os Estudantes.",
            description = "Retorna uma List com todos os Estudantes cadastrados no sistema.",
            tags = {"Students"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List contendo todos os Estudantes retornada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = StudentResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
    })
    @GetMapping("/all")
    public ResponseEntity<List<StudentResponseDTO>> getAllStudents() {
        return ResponseEntity.ok().body(studentService.getAllStudents());
    }

    @GetMapping
    public ResponseEntity<List<StudentResponseDTO>> getStudentsByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(studentService.getStudentsByStatus(status));
    }

    @GetMapping("/me")
    public ResponseEntity<StudentResponseDTO> getCurrentStudent(Authentication auth) {
        String email = auth.getName();

        return ResponseEntity.ok().body(studentService.getCurrentStudent(email));
    }

    @PostMapping
    public ResponseEntity<StudentResponseDTO> createStudent(@Valid @RequestBody StudentRequestDTO studentRequestDTO, UriComponentsBuilder componentsBuilder) {
        StudentResponseDTO student = studentService.createStudent(studentRequestDTO);

        URI uri = componentsBuilder.path("{/id}").buildAndExpand(student.id()).toUri();

        return ResponseEntity.created(uri).body(student);
    }

    @PatchMapping("/me")
    public ResponseEntity<StudentResponseDTO> updateCurrentStudent(Authentication auth, @Valid @RequestBody StudentUpdateDTO studentUpdateDTO) {
        String email = auth.getName();

        return ResponseEntity.ok().body(studentService.updateCurrentStudent(email, studentUpdateDTO));
    }

    @PatchMapping("/{studentId}")
    public ResponseEntity<Void> updateStudentStatus(@PathVariable UUID studentId, @Valid @RequestBody UpdateEntityStatusDTO newStudentStatus) {
        studentService.updateStudentStatus(studentId, newStudentStatus.status());
        return ResponseEntity.noContent().build();
    }

}
