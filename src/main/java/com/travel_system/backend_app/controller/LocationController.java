package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/location")
@RequireAccess(AccessScope.ON_GOING)
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }


    @PostMapping("/{studentTravelId}")
    public ResponseEntity<Void> studentPosition(@PathVariable UUID studentTravelId, @RequestBody LiveCoordinates coordinates) {
        locationService.updateStudentPosition(studentTravelId, coordinates);
        return ResponseEntity.noContent().build();
    }
}
