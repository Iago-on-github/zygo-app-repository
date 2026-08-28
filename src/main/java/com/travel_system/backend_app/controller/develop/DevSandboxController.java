package com.travel_system.backend_app.controller.develop;

import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/private-test")
@Profile("dev")
public class DevSandboxController {
    // testes de fluxos e endpoints que não devem/precisam ser expostos ao público

    private final TravelTrackingService travelTrackingService;
    private final RouteCalculationService routeCalculationService;
    private final MapboxAPIService mapboxAPIService;
    private final PushNotificationService pushNotificationService;

    public DevSandboxController(TravelTrackingService travelTrackingService, RouteCalculationService routeCalculationService, MapboxAPIService mapboxAPIService, PushNotificationService pushNotificationService) {
        this.travelTrackingService = travelTrackingService;
        this.routeCalculationService = routeCalculationService;
        this.mapboxAPIService = mapboxAPIService;
        this.pushNotificationService = pushNotificationService;
    }

    @GetMapping("/{travelId}/location")
    public ResponseEntity<Void> processNewLocation(@PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        travelTrackingService.processNewLocation(vehicleLocationRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deviation")
    public ResponseEntity<RouteDeviationDTO> isRouteDeviation(@RequestBody RouteDeviationRequestDTO routeDeviationRequestDTO) {
        RouteDeviationDTO deviationDTO = routeCalculationService.isRouteDeviation(routeDeviationRequestDTO);
        return ResponseEntity.ok().body(deviationDTO);
    }

    @GetMapping("/calculate")
    public ResponseEntity<RouteDetailsDTO> calculateRoute(
            @RequestParam double originLong,
            @RequestParam double originLat,
            @RequestParam double destLong,
            @RequestParam double destLat) {
        return ResponseEntity.ok().body(mapboxAPIService.calculateRoute(originLong, originLat, destLong, destLat, List.of()));
    }

    @PostMapping("/{travelId}")
    public ResponseEntity<Void> processVehicleMovement(@PathVariable UUID travelId, @RequestBody VehicleLocationRequestDTO vehicleLocationRequest) {
        pushNotificationService.processVehicleMovement(vehicleLocationRequest);
        return ResponseEntity.ok().build();
    }
}
