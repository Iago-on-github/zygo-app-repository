package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.StandardRouteService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/standard-route")
public class StandardRouteController {
    private final StandardRouteService standardRouteService;

    public StandardRouteController(StandardRouteService standardRouteService) {
        this.standardRouteService = standardRouteService;
    }

    @GetMapping("/all")
    public ResponseEntity<Page<StandardRouteResponseDTO>> getAllStandardRoutes() {
        return ResponseEntity.ok().body(standardRouteService.getAllStandardRoutes());
    }

    @GetMapping("/{standardRouteId}")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteById(@PathVariable UUID standardRouteId) {
        return ResponseEntity.ok().body(standardRouteService.getStandardRouteById(standardRouteId));
    }

    @GetMapping("/{customerId}/customer")
    public ResponseEntity<Page<StandardRouteResponseDTO>> getAllStandardRouteByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok().body(standardRouteService.getAllStandardRouteByCustomer(customerId));
    }

    @GetMapping("/{standardRouteId}/route-stops")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteStopPoints(@PathVariable UUID standardRouteId, @RequestParam GeneralStatus status) {
        return ResponseEntity.ok().body(standardRouteService.getStandardRouteStopPoints(standardRouteId, status));
    }

    @PostMapping("/new")
    public ResponseEntity<StandardRouteResponseDTO> createStandardRoute(Authentication auth, @RequestBody StandardRouteRequestDTO standardRouteRequestDTO, UriComponentsBuilder uriComponentsBuilder) {
        String userAuthenticatedEmail = auth.getName();
        StandardRouteResponseDTO newStandardRoute = standardRouteService.createStandardRoute(userAuthenticatedEmail, standardRouteRequestDTO);

        URI uri = uriComponentsBuilder.path("/{id}").buildAndExpand(newStandardRoute.id()).toUri();

        return ResponseEntity.created(uri).body(newStandardRoute);
    }

    @PatchMapping("/{standardRouteId}/update")
    public ResponseEntity<StandardRouteResponseDTO> updateStandardRoute(Authentication auth, @PathVariable UUID standardRouteId, @RequestBody StandardRouteUpdateDTO standardRouteUpdateDTO) {
        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(standardRouteService.updateStandardRoute(standardRouteId, authenticatedEmail, standardRouteUpdateDTO));
    }

}
