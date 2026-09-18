package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.service.RouteStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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


    @GetMapping("/{name}")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopByName(@PathVariable String name) {
        return ResponseEntity.ok().body(routeStopService.getRouteStopByName(name));
    }

    @GetMapping("/{routeStopId}/route")
    public ResponseEntity<RouteStopResponseDTO> getRouteStopById(@PathVariable UUID routeStopId) {
        return ResponseEntity.ok().body(routeStopService.getRouteStopById(routeStopId));
    }

    @PostMapping("/new")
    public ResponseEntity<RouteStopResponseDTO> createRouteStop(@Valid @RequestBody RouteStopRequestDTO routeStopRequestDTO, UriComponentsBuilder componentsBuilder) {
        RouteStopResponseDTO newRouteStop = routeStopService.createRouteStop(routeStopRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(newRouteStop.id()).toUri();

        return ResponseEntity.created(uri).body(newRouteStop);
    }

    @PatchMapping("/{routeStopId}/update")
    public ResponseEntity<RouteStopResponseDTO> updateRouteStop(@PathVariable UUID routeStopId, @Valid @RequestBody RouteStopUpdateDTO routeStopUpdateDTO) {
        RouteStopResponseDTO response = routeStopService.updateRouteStop(routeStopId, routeStopUpdateDTO);

        return ResponseEntity.ok().body(response);
    }

    @PatchMapping("/{routeStopId}/status")
    public ResponseEntity<Void> updateRouteStopStatus(@PathVariable UUID routeStopId, @RequestParam GeneralStatus status) {
        routeStopService.updateRouteStopStatus(routeStopId, status);

        return ResponseEntity.noContent().build();
    }
}
