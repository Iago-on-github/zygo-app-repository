package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.interfaces.RealTimeMessagingContract;
import com.travel_system.backend_app.model.dtos.mensageria.MessagingDTO;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GpsService implements RealTimeMessagingContract {
    private final RabbitTemplate rabbitTemplate;

    public GpsService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendLocalizationToNotification(String city, UUID travelId, MessagingDTO messagingDTO) {
        final String ROUTING_GPS_KEY = "v1.gps." + city + "." + travelId;

        // QoS 0: Mensagem não persistente
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_GPS_NAME, ROUTING_GPS_KEY, messagingDTO, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            return message;
        });
    }

}
