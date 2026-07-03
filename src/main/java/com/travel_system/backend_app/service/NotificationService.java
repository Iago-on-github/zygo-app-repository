package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.interfaces.NotificationMessagingContract;
import com.travel_system.backend_app.model.dtos.mensageria.StudentProximityNotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService implements NotificationMessagingContract {

    private final RabbitTemplate rabbitTemplate;
    private final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendMessage(StudentProximityNotificationMessage dataEvent) {
        logger.info("Received message: {}", dataEvent);
        // QoS 1: Mensagem persistente
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NOTIFICATION_NAME, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, dataEvent, event -> {
            event.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return event;
        });
    }

    @Override
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ERR_DLQ)
    public void processFailedMessagesRetryWithParkingLotStrategy(Message failedMessage) {
        final int MAX_RETRIES_COUNT = 3;
        final String HEADER_X_RETRIES_COUNT = "x-retries-count";

        logger.info("Received failed message: {}", failedMessage.toString());

        Integer retiresCount = (Integer) failedMessage.getMessageProperties().getHeaders().get(HEADER_X_RETRIES_COUNT);
        if (retiresCount == null) retiresCount = 1;
        if (retiresCount >= MAX_RETRIES_COUNT) {
            logger.info("Send message to the parking lot queue");
            rabbitTemplate.send(RabbitMQConfig.EXCHANGE_PARKING_LOT,
                    RabbitMQConfig.ROUTING_KEY_PARKING_LOT,
                    failedMessage);
            return;
        }
        logger.info("Retrying message for the {} time", retiresCount);
        failedMessage.getMessageProperties().getHeaders().put(HEADER_X_RETRIES_COUNT, ++retiresCount);
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_NOTIFICATION_NAME,
                failedMessage.getMessageProperties().getReceivedRoutingKey(),
                failedMessage);
    }
}
