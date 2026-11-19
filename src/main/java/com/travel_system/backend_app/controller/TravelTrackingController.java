package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.dtos.route.TravelTrackingSummaryDTO;
import com.travel_system.backend_app.service.TravelTrackingService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/v1/tracking")
@RequireAccess(AccessScope.ON_GOING)
public class TravelTrackingController {

    private final TravelTrackingService travelTrackingService;

    public TravelTrackingController(TravelTrackingService travelTrackingService) {
        this.travelTrackingService = travelTrackingService;
    }

    @PostMapping("/travels/{travelId}/student/{studentTravelId}/locations")
    public ResponseEntity<Void> markDriverCheckpoint(@PathVariable UUID travelId, @PathVariable UUID studentTravelId, @Valid @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.markDriverCheckpoint(studentTravelId, travelId, vehicleLocationRequest);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/travels/{travelId}/location")
    public ResponseEntity<TravelTrackingSummaryDTO> getDriverPosition(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getDriverPosition(travelId));
    }


    @GetMapping("/travels/{travelId}/history")
    public ResponseEntity<Page<LocationPointDTO>> getTravelHistory(@PathVariable UUID travelId) {
        return ResponseEntity.ok().body(travelTrackingService.getTravelHistory(travelId));
    }
}
