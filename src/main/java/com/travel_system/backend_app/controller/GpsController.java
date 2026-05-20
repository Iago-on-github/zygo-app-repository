package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.GpsDataIngestorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/gps")
public class GpsController {
    private final GpsDataIngestorService gpsDataIngestorService;
    private final TravelRepository travelRepository;

    private final Logger log = LoggerFactory.getLogger(GpsController.class);

    public GpsController(GpsDataIngestorService gpsDataIngestorService, TravelRepository travelRepository) {
        this.gpsDataIngestorService = gpsDataIngestorService;
        this.travelRepository = travelRepository;
    }

    @PostMapping("/updateGpsData")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> vehicleGps(@RequestParam("city") String city, @RequestParam("travelId") String travelId, @RequestBody VehicleLocationRequestDTO vehicleLocation) {
        UUID travelConvertedId = UUID.fromString(travelId);

        boolean existsTravel = travelRepository.existsByIdAndTravelStatus(travelConvertedId, TravelStatus.TRAVELLING);

        if (!existsTravel) {
            log.warn("Viagem não encontrada ou não está em andamento. Não envia nada ao rabbitmq: {} ", travelId);
            return ResponseEntity.notFound().build();
        }

        gpsDataIngestorService.sendVehicleGps(city, travelId, vehicleLocation);

        log.info("Viagem mapeada com sucesso, enviando os dados ao rabbitmq... {} ", travelId);
        return ResponseEntity.accepted().build();
    }
}
