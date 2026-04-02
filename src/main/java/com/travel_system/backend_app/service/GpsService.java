package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.interfaces.RealTimeMessagingContract;
import com.travel_system.backend_app.model.dtos.mensageria.MessagingDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GpsService implements RealTimeMessagingContract {
    private final RabbitTemplate rabbitTemplate;

    private Logger logger = LoggerFactory.getLogger(GpsService.class);

    public GpsService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendLocalizationToNotification(String city, UUID travelId, MessagingDTO messagingDTO) {
        if (city == null || travelId == null || messagingDTO == null) {
            logger.debug("[sendLocalizationToNotification] - parâmetros inválidos para envio ao rabbitMQ: {} {} {}", city, travelId, messagingDTO);
            return;
        }

        final String ROUTING_GPS_KEY = "v1.gps." + city + "." + travelId;

        // QoS 0: Mensagem não persistente
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_GPS_NAME, ROUTING_GPS_KEY, messagingDTO, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            logger.info("[sendLocalizationToNotification] - msg enviada ao rabbitMQ com sucesso");
            return message;
        });
    }

}
