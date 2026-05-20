package com.travel_system.backend_app.service;

import com.google.api.client.util.Value;
import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class GpsDataIngestorService {

    private final RabbitTemplate rabbitTemplate;

    public GpsDataIngestorService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // envia ao rabbitmq as informações de gps da viagem + a routing_key contendo ids específicos
    public void sendVehicleGps(String city, String travelId, VehicleLocationRequestDTO vehicleLocation) {
        final String ROUTING_KEY = "v1.gps." + city + "." + travelId;

        Instant now = Instant.now();
        UUID cityId = UUID.fromString(city);

        GpsPayload gpsPayload = new GpsPayload(
                vehicleLocation.latitude(),
                vehicleLocation.longitude(),
                vehicleLocation.speed(),
                vehicleLocation.heading(),
                now,
                vehicleLocation.travelId(),
                cityId
        );

        // QoS 0
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_GPS_NAME, ROUTING_KEY, gpsPayload, location -> {
            location.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            return location;
        });
    }
}
