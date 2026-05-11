package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.service.TravelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/travel")
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

    @PostMapping("/start/{travelId}")
    public ResponseEntity<Void> startTravel(@PathVariable UUID travelId) {
        travelService.startTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/end/{travelId}")
    public ResponseEntity<Void> endTravel(@PathVariable UUID travelId) {
        travelService.endTravel(travelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join/{travelId}/{studentId}")
    public ResponseEntity<Void> joinTravel(@PathVariable UUID travelId, @PathVariable UUID studentId) {
        travelService.joinTravel(travelId, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leave/{travelId}/{studentId}")
    public ResponseEntity<Void> leaveTravel (@PathVariable UUID travelId, @PathVariable UUID studentId) {
        travelService.leaveTravel(travelId, studentId);
        return ResponseEntity.noContent().build();
    }
}
