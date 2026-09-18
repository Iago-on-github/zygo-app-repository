package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteStopsUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.StandardRouteUpdateDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.service.StandardRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
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
    public ResponseEntity<Page<StandardRouteResponseDTO>> getAllStandardRoutes(@PageableDefault(size = 10) @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().body(standardRouteService.getAllStandardRoutes(pageable));
    }

    @GetMapping("/{standardRouteId}")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteById(@PathVariable UUID standardRouteId) {
        return ResponseEntity.ok().body(standardRouteService.getStandardRouteById(standardRouteId));
    }

    @GetMapping("/{standardRouteId}/route-stops")
    public ResponseEntity<StandardRouteResponseDTO> getStandardRouteStopPoints(@PathVariable UUID standardRouteId, @RequestParam GeneralStatus status, @RequestParam TravelDirection travelDirection) {
        return ResponseEntity.ok().body(standardRouteService.getStandardRouteStopPoints(standardRouteId, status, travelDirection));
    }

    @PostMapping("/new")
    public ResponseEntity<StandardRouteResponseDTO> createStandardRoute(@Valid @RequestBody StandardRouteRequestDTO standardRouteRequestDTO, UriComponentsBuilder uriComponentsBuilder) {
        StandardRouteResponseDTO newStandardRoute = standardRouteService.createStandardRoute(standardRouteRequestDTO);

        URI uri = uriComponentsBuilder.path("/{id}").buildAndExpand(newStandardRoute.id()).toUri();

        return ResponseEntity.created(uri).body(newStandardRoute);
    }

    @PatchMapping("/{standardRouteId}/update")
    public ResponseEntity<StandardRouteResponseDTO> updateStandardRoute(@PathVariable UUID standardRouteId, @RequestParam TravelDirection travelDirection, @Valid @RequestBody StandardRouteUpdateDTO standardRouteUpdateDTO) {
        StandardRouteResponseDTO response = standardRouteService.updateStandardRoute(standardRouteId, travelDirection, standardRouteUpdateDTO);

        return ResponseEntity.ok().body(response);
    }

}
