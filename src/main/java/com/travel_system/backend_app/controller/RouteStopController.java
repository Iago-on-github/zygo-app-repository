package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.RouteStopService;
import org.apache.tomcat.util.http.parser.Authorization;
import org.simpleframework.xml.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/route-stops")
public class RouteStopController {
    private final RouteStopService routeStopService;

    public RouteStopController(RouteStopService routeStopService) {
        this.routeStopService = routeStopService;
    }

    @GetMapping("/{customerId}/customer")
    public ResponseEntity<List<RouteStopResponseDTO>> getRouteStopsByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok().body(routeStopService.getRouteStopsByCustomer(customerId));
    }

    @GetMapping("/{name}")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopByName(@PathVariable String name) {
        return ResponseEntity.ok().body(routeStopService.getRouteStopByName(name));
    }

    @GetMapping("/{routeStopId}/route")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopById(@PathVariable UUID routeStopId) {
        return ResponseEntity.ok().body(routeStopService.getRouteStopById(routeStopId));
    }

    @PostMapping("/new")
    public ResponseEntity<RouteStopResponseDTO> createRouteStop(Authentication auth, @Valid @RequestBody RouteStopRequestDTO routeStopRequestDTO, UriComponentsBuilder componentsBuilder) {
        String authenticatedEmail = auth.getName();
        RouteStopResponseDTO newRouteStop = routeStopService.createRouteStop(authenticatedEmail, routeStopRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newRouteStop.id()).toUri();

        return ResponseEntity.created(uri).body(newRouteStop);
    }

    @PatchMapping("/{routeStopId}/update")
    public ResponseEntity<RouteStopResponseDTO> updateRouteStop(Authentication auth, @PathVariable UUID routeStopId, @Valid @RequestBody RouteStopUpdateDTO routeStopUpdateDTO) {
        String authenticatedEmail = auth.getName();

        return ResponseEntity.ok().body(routeStopService.updateRouteStop(authenticatedEmail, routeStopId, routeStopUpdateDTO));
    }

    @PatchMapping("/{standardRouteId}/status")
    public ResponseEntity<Void> updateRouteStopStatus(@PathVariable UUID standardRouteId, Authentication auth, @RequestParam GeneralStatus status) {
        String authenticatedEmail = auth.getName();

        routeStopService.updateRouteStopStatus(standardRouteId, authenticatedEmail, status);

        return ResponseEntity.noContent().build();
    }
}
