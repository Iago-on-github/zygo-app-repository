package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.service.PushNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/travel/notifications")
public class PushNotificationController {

    private final PushNotificationService pushNotificationService;

    public PushNotificationController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/{travelId}")
    public ResponseEntity<Void> processVehicleMovement(@PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        pushNotificationService.processVehicleMovement(vehicleLocationRequest);
        return ResponseEntity.ok().build();
    }
}
