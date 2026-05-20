package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.service.RouteCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route")
public class RouteCalculationController {
    private final RouteCalculationService routeCalculationService;

    public RouteCalculationController(RouteCalculationService routeCalculationService) {
        this.routeCalculationService = routeCalculationService;
    }

    @PostMapping("/deviation")
    public ResponseEntity<RouteDeviationDTO> isRouteDeviation(@RequestBody RouteDeviationRequestDTO routeDeviationRequestDTO) {
        RouteDeviationDTO deviationDTO = routeCalculationService.isRouteDeviation(routeDeviationRequestDTO);
        return ResponseEntity.ok().body(deviationDTO);
    }
}
