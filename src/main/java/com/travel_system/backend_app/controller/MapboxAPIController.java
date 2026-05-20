package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.mapboxApi.MapboxApiResponse;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.service.MapboxAPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/mapbox")
public class MapboxAPIController {
    private final MapboxAPIService mapboxAPIService;

    public MapboxAPIController(MapboxAPIService mapboxAPIService) {
        this.mapboxAPIService = mapboxAPIService;
    }

    @GetMapping("/calculate")
    public ResponseEntity<RouteDetailsDTO> calculateRoute(
            @RequestParam double originLong,
            @RequestParam double originLat,
            @RequestParam double destLong,
            @RequestParam double destLat) {
        return ResponseEntity.ok().body(mapboxAPIService.calculateRoute(originLong, originLat, destLong, destLat));
    }
}
