package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import com.travel_system.backend_app.service.RedisTrackingService;
import com.travel_system.backend_app.service.TravelHistoryPingsService;
import com.travel_system.backend_app.service.TravelTrackingNotificationService;
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
    private final TravelTrackingNotificationService trackingNotificationService;

    public RabbitMQConsumers(RedisTrackingService redisTrackingService, TravelHistoryPingsService travelHistoryPingsService, TravelTrackingNotificationService trackingNotificationService) {
        this.redisTrackingService = redisTrackingService;
        this.travelHistoryPingsService = travelHistoryPingsService;
        this.trackingNotificationService = trackingNotificationService;
    }

    private final Logger logger = LoggerFactory.getLogger(RabbitMQConsumers.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_NAME)
    public void receiveMessages(StudentProximityNotificationMessage event) {
        logger.info("method receiveMessage received message: {}", event);

        // envia notificação ao firebase
        trackingNotificationService.sendCheckProximityAlertsNotification(event);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PROCESSING_COORDINATES)
    public void processingMessagesGpsCoordinates(GpsPayload gpsPayload) {
        if (gpsPayload == null
                || gpsPayload.travelId() == null
                || gpsPayload.cityId() == null
                || gpsPayload.latitude() == null
                || gpsPayload.longitude() == null) {
            logger.warn("gpsPayload inválido: campos obrigatórios ausentes, descartando mensagem. Payload: {}", gpsPayload);
            return;
        }

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
