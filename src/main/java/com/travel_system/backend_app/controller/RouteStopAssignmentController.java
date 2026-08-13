package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.service.RouteStopAssignmentService;
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
    public ResponseEntity<Void> associateRouteStopWithStandardRoute(@PathVariable UUID standardRouteId, @PathVariable UUID routeStopId, @RequestParam int sequence, @RequestParam boolean isOptionalSpot) {
        routeStopAssignmentService.associateRouteStopWithStandardRoute(standardRouteId, routeStopId, sequence, isOptionalSpot);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{standardRouteId}/remove/{routeStopId}")
    public ResponseEntity<Void> removeRouteStopWithStandardRoute(@PathVariable UUID standardRouteId, @PathVariable UUID routeStopId) {
        routeStopAssignmentService.removeRouteStopWithStandardRoute(standardRouteId, routeStopId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{standardRouteId}/reorder/{routeStopId}")
    public ResponseEntity<StandardRouteResponseDTO> reorderRouteStops(@PathVariable UUID standardRouteId, @Valid @RequestBody List<RouteStopReorderRequestDTO> routeStopsReorder) {
        return ResponseEntity.ok().body(routeStopAssignmentService.reorderRouteStops(standardRouteId, routeStopsReorder));
    }
}
