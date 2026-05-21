package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.service.TravelService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
    public ResponseEntity<Void> joinTravel(@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.joinTravel(travelId, studentEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{travelId}/leave")
    public ResponseEntity<Void> leaveTravel (@PathVariable UUID travelId, Authentication authentication) {
        String studentEmail = authentication.getName(); // email do student logado

        travelService.leaveTravel(travelId, studentEmail);
        return ResponseEntity.noContent().build();
    }
}
