package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.mensageria.SendPackageDataToRabbitMQ;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.TravelHistoryPingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RabbitMQConsumers {
    private final RedisTrackingService redisTrackingService;
    private final TravelHistoryPingsService travelHistoryPingsService;

    public RabbitMQConsumers(RedisTrackingService redisTrackingService, TravelHistoryPingsService travelHistoryPingsService) {
        this.redisTrackingService = redisTrackingService;
        this.travelHistoryPingsService = travelHistoryPingsService;
    }

    private final Logger logger = LoggerFactory.getLogger(RabbitMQConsumers.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_NAME)
    public void receiveMessages(SendPackageDataToRabbitMQ event) {
        logger.info("method receiveMessage received message: {}", event);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PROCESSING_COORDINATES)
    public void processingMessagesGpsCoordinates(GpsPayload gpsPayload) {
        logger.info("Method processingMessagesGpsCoordinates received message: {}", gpsPayload);

        Instant now = Instant.now();
        UUID travelId = gpsPayload.travelId();
        UUID cityId = gpsPayload.cityId();
        Double latitude = gpsPayload.latitude();
        Double longitude = gpsPayload.longitude();
        Double heading = gpsPayload.heading();
        Double speed = gpsPayload.speed();

        // datahistory dos pings durante a viagem
        boolean locationUpdateAllowed = redisTrackingService.isLocationUpdateAllowed(travelId);

        if (!locationUpdateAllowed) return;

        travelHistoryPingsService.saveTravelLocationHistoryData(cityId.toString(), travelId.toString(), now,
                new VehicleLocationRequestDTO(travelId, latitude, longitude, speed, heading));

        // salva os novos pings que chegarão
        redisTrackingService.saveHistoryPingLocation(travelId, now);
    }
}
