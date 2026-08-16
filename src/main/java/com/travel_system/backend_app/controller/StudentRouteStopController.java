package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopStudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.service.StudentRouteStopService;
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

    @GetMapping("/{studentId}/routestop/{standardRouteId}")
    public ResponseEntity<List<StudentRouteStopAssociateResponseDTO>> getStudentRouteStops(Authentication auth, @PathVariable UUID studentId, @PathVariable UUID standardRouteId) {
        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.getStudentRouteStops(authenticatedEmail, studentId, standardRouteId));
    }

    @PostMapping("/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> getStudentRouteStopsByPeriodAndStandardRoute(Authentication auth, @PathVariable UUID standardRouteId, @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.getStudentRouteStopsByPeriodAndStandardRoute(authenticatedEmail, standardRouteId, routeStopStudentsRequestDTO));
    }

    @PatchMapping("/{routeStopId}/associate/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> associateStudentWithRouteStop(
            Authentication auth,
            @PathVariable UUID routeStopId,
            @PathVariable UUID standardRouteId,
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.associateStudentWithRouteStop(
                authenticatedEmail,
                routeStopId,
                standardRouteId,
                routeStopStudentsRequestDTO));
    }

    @PutMapping("/{studentId}/update/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> updateStudentRouteStops(
            Authentication auth,
            @PathVariable UUID studentId,
            @PathVariable UUID standardRouteId,
            @Valid @RequestBody RouteStopStudentUpdateDTO routeStopStudentUpdateDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.updateStudentRouteStops(authenticatedEmail, studentId, standardRouteId, routeStopStudentUpdateDTO));
    }

    @DeleteMapping("/{routeStopId}/remove/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> removeStudentToRouteStop(
            Authentication auth,
            @PathVariable UUID routeStopId,
            @PathVariable UUID standardRouteId,
            @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {

        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(studentRouteStopService.removeStudentFromRouteStop(
                authenticatedEmail,
                routeStopId,
                standardRouteId,
                routeStopStudentsRequestDTO));
    }
}
