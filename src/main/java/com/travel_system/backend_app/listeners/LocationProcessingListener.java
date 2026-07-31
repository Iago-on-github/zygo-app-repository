package com.travel_system.backend_app.listeners;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.EtaDataStatesInvalidException;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.PushNotificationService;
import com.travel_system.backend_app.service.TravelTrackingService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocationProcessingListener {
    private final TravelTrackingService travelTrackingService;
    private final PushNotificationService pushNotificationService;

    private final Logger logger = LoggerFactory.getLogger(LocationProcessingListener.class);

    public LocationProcessingListener(TravelTrackingService travelTrackingService, PushNotificationService pushNotificationService) {
        this.travelTrackingService = travelTrackingService;
        this.pushNotificationService = pushNotificationService;
    }

    @Async
    @EventListener
    public void handleLocationProcessing(NewLocationReceivedEvents locationReceivedEvents) {
        VehicleLocationRequestDTO vehicleLocationRequest = getVehicleLocationRequestDTO(locationReceivedEvents);
        try {
            travelTrackingService.processNewLocation(vehicleLocationRequest);
        } catch(EtaDataStatesInvalidException e) {
            logger.warn("[handleLocationProcessing] processNewLocation falhou para viagem {}: {}",
                    locationReceivedEvents.travelId(), e.getMessage());
        }

        // Processa alertas de proximidade e movimento
        // usa políticas de retry padrões do Spring
        pushNotificationService.checkProximityAlerts(vehicleLocationRequest);
        pushNotificationService.processVehicleMovement(vehicleLocationRequest);
    }

    private static VehicleLocationRequestDTO getVehicleLocationRequestDTO(NewLocationReceivedEvents locationReceivedEvents) {
        UUID travelId = locationReceivedEvents.travelId();
        Double latitude = locationReceivedEvents.latitude();
        Double longitude = locationReceivedEvents.longitude();
        Double heading = locationReceivedEvents.heading();
        Double speed = locationReceivedEvents.speed();

        return new VehicleLocationRequestDTO(
                travelId,
                latitude,
                longitude,
                speed,
                heading);
    }
}
