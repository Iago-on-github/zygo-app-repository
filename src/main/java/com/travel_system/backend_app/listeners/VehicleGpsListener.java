package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.service.GpsDataIngestorService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VehicleGpsListener {
    private final GpsDataIngestorService gpsDataIngestorService;

    public VehicleGpsListener(GpsDataIngestorService gpsDataIngestorService) {
        this.gpsDataIngestorService = gpsDataIngestorService;
    }

    @Async("vehicleGpsTaskExecutor")
    @EventListener
    public void handleVehicleGps(VehicleGpsMessageDTO vehicleGpsMessageDTO) {
        gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);
    }
}
