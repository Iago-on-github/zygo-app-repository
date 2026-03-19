package com.travel_system.backend_app.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_NOTIFICATION_NAME = "queue.notification";
    public static final String QUEUE_ERR_DLQ = "dlq.queue";
    public static final String QUEUE_PROCESSING_COORDINATES = "process.gps.coords";

    public static final String EXCHANGE_NOTIFICATION_NAME = "push.distance.notification";
    public static final String EXCHANGE_GPS_NAME = "tg.gps.exchange";
    public static final String EXCHANGE_ERR_DLX = "notification.dlx";

    public static final String NOTIFICATION_ROUTING_KEY = "notification.distance";
    public static final String ERR_ROUTING_KEY = "notification.error";

    public static final String QUEUE_PARKING_LOT = QUEUE_NOTIFICATION_NAME + ".parking-lot";
    public static final String EXCHANGE_PARKING_LOT = QUEUE_NOTIFICATION_NAME + ".exchange.parking-lot";
    public static final String ROUTING_KEY_PARKING_LOT = "notification.parking-lot";

    @Bean
    public Queue queueNotification() {
        return QueueBuilder.durable("queue.notification")
                .withArgument("x-dead-letter-exchange", EXCHANGE_ERR_DLX)
                .withArgument("x-dead-letter-routing-key", ERR_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue queueErr() {
        return new Queue(QUEUE_ERR_DLQ, true);
    }

    @Bean
    public Queue queueParkingLot() {
        return new Queue(QUEUE_PARKING_LOT, true);
    }

    @Bean
    public Queue processingGpsCoordinates() {
        return new Queue(QUEUE_PROCESSING_COORDINATES, true);
    }

    @Bean
    public TopicExchange exchangeNotification() {
        return new TopicExchange(EXCHANGE_NOTIFICATION_NAME);
    }

    @Bean
    public TopicExchange exchangeGps() {
        return new TopicExchange(EXCHANGE_GPS_NAME);
    }

    @Bean
    public TopicExchange exchangeError() {
        return new TopicExchange(EXCHANGE_ERR_DLX);
    }

    @Bean
    public TopicExchange parkingLotExchange() {
        return new TopicExchange(EXCHANGE_PARKING_LOT);
    }


    // faz o mqtt olhar para a exchange custom e não para a padrão amqTopic
    @Bean
    public Binding bindGpsExchangeToTopicExchange() {
        TopicExchange amqTopic = new TopicExchange("amq.topic");
        return BindingBuilder.bind(amqTopic).to(exchangeGps()).with("#");
    }

    @Bean
    public Binding bindingNotification(Queue queueNotification, TopicExchange exchangeNotification) {
        return BindingBuilder.bind(queueNotification).to(exchangeNotification).with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding bindingErr(Queue queueErr, TopicExchange exchangeError) {
        return BindingBuilder.bind(queueErr).to(exchangeError).with(ERR_ROUTING_KEY);
    }

    @Bean
    public Binding bindingParkingLot(Queue queueParkingLot, TopicExchange parkingLotExchange) {
        return BindingBuilder.bind(queueParkingLot).to(parkingLotExchange).with(ROUTING_KEY_PARKING_LOT);
    }

    @Bean
    public Binding bindingProcessGpsCoordinates(Queue queueProcessGpsCoordinates, TopicExchange exchangeGpsName) {
        // usa "v1.gps.#" para capturar todas as cidades
        return BindingBuilder.bind(queueProcessGpsCoordinates).to(exchangeGpsName).with("v1.gps.#");
    }

    // serialização
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

}
