package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.service.TravelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/travel")
public class TravelController {

    private final TravelService travelService;

    public TravelController(TravelService travelService) {
        this.travelService = travelService;
    }

    @PostMapping("/create")
    public ResponseEntity<TravelResponseDTO> createTravel(@RequestBody TravelRequestDTO travelRequestDTO, UriComponentsBuilder componentsBuilder) {
        TravelResponseDTO responseDTO = travelService.createTravel(travelRequestDTO);

        URI uri = componentsBuilder.path("/{id}").buildAndExpand(responseDTO.id()).toUri();

        return ResponseEntity.created(uri).body(responseDTO);
    }

    @PostMapping("/{travelId}/start")
    public ResponseEntity<Void> startTravel(@PathVariable UUID travelId) {
        travelService.startTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/end")
    public ResponseEntity<Void> endTravel(@PathVariable UUID travelId) {
        travelService.endTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/join")
    public ResponseEntity<JoinTravelResponseDTO> joinTravel(@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        return ResponseEntity.ok().body(travelService.joinTravel(travelId, studentEmail, StudentTravelStatus.ACTIVE));
    }

    @PutMapping("/{travelId}/change/{driverId}")
    public ResponseEntity<Void> driverChange(@PathVariable UUID travelId, @PathVariable UUID driverId) {
        travelService.driverChanged(travelId, driverId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{travelId}/cancel")
    public ResponseEntity<Void> cancelTravel(@PathVariable UUID travelId) {
        travelService.cancelTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/leave")
    public ResponseEntity<Void> leaveTravel (@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.leaveTravel(travelId, studentEmail, StudentTravelStatus.LEFT);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activeTrips")
    public ResponseEntity<ActiveStudentTravelDTO> getActiveTravelByStudent(Authentication auth) {
        String studentEmail = auth.getName();

        return ResponseEntity.ok().body(travelService.getActiveTravelByStudent(studentEmail));
    }

    @GetMapping("/{travelId}/preview")
    public ResponseEntity<TravelPreviewDTO> getTravelPreview(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelService.getTravelPreview(travelId));
    }

    @GetMapping("/{travelId}/standard")
    public ResponseEntity<StandardRouteResponseDTO> getTravelStandardRoute(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelService.getTravelStandardRoute(travelId));
    }
}
