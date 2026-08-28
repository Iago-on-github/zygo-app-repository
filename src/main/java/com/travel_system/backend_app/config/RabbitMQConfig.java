package com.travel_system.backend_app.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_ERR_DLQ = "dlq.queue";
    public static final String QUEUE_PROCESSING_COORDINATES = "process.gps.coords";

    public static final String EXCHANGE_GPS_NAME = "tg.gps.exchange";


    @Bean
    public Queue queueErr() {
        return new Queue(QUEUE_ERR_DLQ, true);
    }

    @Bean
    public Queue processingGpsCoordinates() {
        return new Queue(QUEUE_PROCESSING_COORDINATES, true);
    }

    @Bean
    public TopicExchange exchangeGps() {
        return new TopicExchange(EXCHANGE_GPS_NAME);
    }


    // faz o mqtt olhar para a exchange custom e não para a padrão amqTopic
    @Bean
    public Binding bindGpsExchangeToTopicExchange() {
        TopicExchange amqTopic = new TopicExchange("amq.topic");
        return BindingBuilder.bind(amqTopic).to(exchangeGps()).with("#");
    }

    @Bean
    public Binding bindingProcessGpsCoordinates(Queue processingGpsCoordinates, TopicExchange exchangeGps) {
        // usa "v1.gps.#" para capturar todas as cidades
        return BindingBuilder.bind(processingGpsCoordinates).to(exchangeGps).with("v1.gps.#");
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
