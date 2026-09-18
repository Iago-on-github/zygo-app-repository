package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.dtos.route.AssociateRouteStopToStandardRouteDTO;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.service.RouteStopAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-assignment")
public class RouteStopAssignmentController {
    private final RouteStopAssignmentService routeStopAssignmentService;

    public RouteStopAssignmentController(RouteStopAssignmentService routeStopAssignmentService) {
        this.routeStopAssignmentService = routeStopAssignmentService;
    }

    @PatchMapping("/{standardRouteId}/associate/{routeStopId}")
    public ResponseEntity<Void> associateRouteStopWithStandardRoute(@PathVariable UUID standardRouteId, @PathVariable UUID routeStopId, @Valid @RequestBody AssociateRouteStopToStandardRouteDTO dto) {

        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, dto);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{standardRouteId}/remove/{routeStopId}")
    public ResponseEntity<Void> removeRouteStopWithStandardRoute(@PathVariable UUID standardRouteId, @PathVariable UUID routeStopId, @RequestParam TravelDirection travelDirection) {
        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId, travelDirection);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{standardRouteId}/reorder")
    public ResponseEntity<StandardRouteResponseDTO> reorderRouteStops(@PathVariable UUID standardRouteId, @Valid @RequestBody List<RouteStopReorderRequestDTO> routeStopsReorder) {
        return ResponseEntity.ok().body(routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder));
    }
}
