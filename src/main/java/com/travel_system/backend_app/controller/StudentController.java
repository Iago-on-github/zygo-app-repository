package com.travel_system.backend_app.controller;

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

    @Operation(
            summary = "Listar todos os Estudantes por Status.",
            description = "Retorna uma lista de estudantes cadastrados filtrada pelo status fornecido. " +
                    "**Nota importante:** Se nenhum status for enviado na requisição, o sistema assumirá por padrão o status ACTIVE.",
            tags = {"Students"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List contendo todos os estudantes retornada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = StudentResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping
    public ResponseEntity<List<StudentResponseDTO>> getStudentsByStatus(@RequestParam(required = false) GeneralStatus status) {
        return ResponseEntity.ok().body(studentService.getStudentsByStatus(status));
    }

    @Operation(
            summary = "Obter o Estudante logado",
            description = "Retorna o atual estudante logado",
            tags = {"Students"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estudante logado retornado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "404", description = "Entidade do estudante não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/me")
    public ResponseEntity<StudentResponseDTO> getCurrentStudent(Authentication auth) {
        String email = auth.getName();

        return ResponseEntity.ok().body(studentService.getCurrentStudent(email));
    }

    @Operation(
            summary = "Criar um novo estudante",
            description = "Cadastra um novo estudante no sistema, valida duplicidade de dados e vincula a permissão 'ROLE_USER'.",
            tags = {"Students"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Estudante criado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponseDTO.class)),
                    headers = @Header(name = "Location", description = "URI do estudante criado (ex: /v1/students/{id})", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida. Possíveis causas:\n" +
                    "- **Campos obrigatórios** inválidos ou não preenchidos devidamente;\n" +
                    "- **E-mail ou Telefone** já cadastrados no sistema por outro usuário;\n" +
                    "- **Permissão 'ROLE_USER'** não encontrada no banco de dados.",
                    content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "401", description = "Não autenticado. Token JWT ausente, expirado ou inválido.",
                    content = @Content(schema = @Schema(hidden = true))),
    })
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
