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

    @GetMapping("/route-stops/{standardRouteId}")
    public ResponseEntity<List<StudentRouteStopAssociateResponseDTO>> getStudentRouteStops(@PathVariable UUID standardRouteId, @RequestParam TravelDirection travelDirection) {
        List<StudentRouteStopAssociateResponseDTO> response = studentRouteStopService.getStudentRouteStops(standardRouteId, travelDirection);

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


    @PutMapping("/update/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> updateStudentRouteStops(@PathVariable UUID standardRouteId, @Valid @RequestBody RouteStopStudentUpdateDTO routeStopStudentUpdateDTO) {
        return ResponseEntity.ok().body(studentRouteStopService.updateStudentRouteStops(standardRouteId, routeStopStudentUpdateDTO));
    }

    @DeleteMapping("/{routeStopId}/remove/{standardRouteId}")
    public ResponseEntity<StudentRouteStopAssociateResponseDTO> removeStudentToRouteStop(@PathVariable UUID routeStopId, @PathVariable UUID standardRouteId, @Valid @RequestBody RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        StudentRouteStopAssociateResponseDTO response = studentRouteStopService.removeStudentFromRouteStop(routeStopId, standardRouteId, routeStopStudentsRequestDTO);
        return ResponseEntity.ok().body(response);
    }
}
