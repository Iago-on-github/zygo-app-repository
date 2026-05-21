package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.service.TravelTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tracking")
public class TravelTrackingController {

    private final TravelTrackingService travelTrackingService;

    public TravelTrackingController(TravelTrackingService travelTrackingService) {
        this.travelTrackingService = travelTrackingService;
    }

    @PostMapping("/travels/{travelId}/locations/{cityId}")
    public ResponseEntity<Void> markDriverCheckpoint(@PathVariable UUID cityId, @PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.markDriverCheckpoint(cityId, travelId, vehicleLocationRequest);
        return ResponseEntity.ok().build();
    }

    /* endpoint temporário para testes */
    @GetMapping("/{travelId}/location")
    public ResponseEntity<Void> processNewLocation(@PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.processNewLocation(vehicleLocationRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/travels/{travelId}/students/{studentId}/embark")
    public ResponseEntity<Void> confirmStudentEmbark(@PathVariable UUID studentId, @PathVariable UUID travelId) {
        travelTrackingService.confirmEmbarkOnTravel(studentId, travelId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/travels/{travelId}/location")
    public ResponseEntity<LiveLocationDTO> getDriverPosition(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getDriverPosition(travelId));
    }

    @GetMapping("/travels/{travelId}/history")
    public ResponseEntity<Page<LocationPointDTO>> getTravelHistory(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getTravelHistory(travelId));
    }
}
