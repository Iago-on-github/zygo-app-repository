package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopStudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.service.StudentRouteStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-stop-students")
public class StudentRouteStopController {
    
    private final StudentRouteStopService studentRouteStopService;

    public StudentRouteStopController(StudentRouteStopService studentRouteStopService) {
        this.studentRouteStopService = studentRouteStopService;
    }

    @GetMapping("/{studentId}/route-stops/{standardRouteId}")
    public ResponseEntity<List<StudentRouteStopAssociateResponseDTO>> getStudentRouteStops(@PathVariable UUID studentId, @PathVariable UUID standardRouteId, @RequestParam TravelDirection travelDirection) {
        List<StudentRouteStopAssociateResponseDTO> response = studentRouteStopService.getStudentRouteStops(studentId, standardRouteId, travelDirection);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> getStudentRouteStopsByPeriodAndStandardRoute(@PathVariable UUID standardRouteId, @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        return ResponseEntity.ok().body(studentRouteStopService.getStudentRouteStopsByPeriodAndDirectionAndStandardRoute(standardRouteId, routeStopStudentsRequestDTO));
    }

    @PatchMapping("/{routeStopId}/associate/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> associateStudentWithRouteStop(@PathVariable UUID routeStopId, @PathVariable UUID standardRouteId, @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        return ResponseEntity.ok().body(studentRouteStopService.associateStudentWithRouteStop(routeStopId, standardRouteId, routeStopStudentsRequestDTO));
    }


    @PutMapping("/{studentId}/update/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> updateStudentRouteStops(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID do estudante que terá o ponto de parada atualizado.", required = true)
            @PathVariable UUID studentId,
            @Parameter(description = "ID da Rota Padrão onde a atualização ocorrerá.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Dados da atualização: ID do novo ponto de parada e o período da viagem.", required = true)
            @Valid @RequestBody RouteStopStudentUpdateDTO routeStopStudentUpdateDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.updateStudentRouteStops(authenticatedEmail, studentId, standardRouteId, routeStopStudentUpdateDTO));
    }

    @DeleteMapping("/{routeStopId}/remove/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> removeStudentToRouteStop(
            @Parameter(hidden = true) Authentication auth,
            @Parameter(description = "ID do Ponto de Parada (Route Stop) que será desvinculado.", required = true)
            @PathVariable UUID routeStopId,
            @Parameter(description = "ID da Rota Padrão (Standard Route) de onde o estudante será removido.", required = true)
            @PathVariable UUID standardRouteId,
            @Parameter(description = "Dados da remoção: ID do estudante e o período da viagem (TravelPeriod) do vínculo.", required = true)
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        StudentRouteStopAssociateResponseDTO response = studentRouteStopService.removeStudentFromRouteStop(
                authenticatedEmail,
                routeStopId,
                standardRouteId,
                routeStopStudentsRequestDTO
        );

        return ResponseEntity.ok().body(response);
    }
}
