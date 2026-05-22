package com.travel_system.backend_app.controller.develop;

import com.travel_system.backend_app.controller.TravelTrackingController;
import com.travel_system.backend_app.model.dtos.mensageria.SendPackageDataToRabbitMQ;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.service.NotificationService;
import com.travel_system.backend_app.service.TravelTrackingService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/private-test")
@Profile("dev")
public class DevSandboxController {
    // testes de fluxos e endpoints que não devem/precisam ser expostos ao público

    private final NotificationService notificationService;
    private final TravelTrackingService travelTrackingService;

    public DevSandboxController(NotificationService notificationService, TravelTrackingService travelTrackingService) {
        this.notificationService = notificationService;
        this.travelTrackingService = travelTrackingService;
    }

    @PostMapping("/sendRabbitMessage")
    public void sendTestMessage() {
        notificationService.sendMessage(new SendPackageDataToRabbitMQ(
                UUID.randomUUID(),
                UUID.randomUUID(),
                350.0,
                "FAR",
                Instant.now().toString(),
                "DISTANCE_STEP_REACHED"));
    }

    @GetMapping("/{travelId}/location")
    public ResponseEntity<Void> processNewLocation(@PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.processNewLocation(vehicleLocationRequest);
        return ResponseEntity.noContent().build();
    }
}
