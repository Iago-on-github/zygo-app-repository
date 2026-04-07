package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.mensageria.SendPackageDataToRabbitMQ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT)
     */

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ArgumentCaptor<MessagePostProcessor> msgPostProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

    @Nested
    class sendMessage {

        @Test
        @DisplayName("should send message to rabbitmq with success")
        void shouldSendMessageToRabbitMqWithSuccess() {
            // arrange
            SendPackageDataToRabbitMQ sendPackageData = new SendPackageDataToRabbitMQ(
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    UUID.fromString("987e6543-e21b-45d3-b321-123456789abc"),
                    123.5,
                    "ZONE_A",
                    Instant.now().toString(),
                    "ZONE_CHANGED"
            );

            // act
            notificationService.sendMessage(sendPackageData);

            // assert
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(RabbitMQConfig.EXCHANGE_NOTIFICATION_NAME),
                    eq(RabbitMQConfig.NOTIFICATION_ROUTING_KEY),
                    argThat(packDataProp -> {
                        SendPackageDataToRabbitMQ msg = (SendPackageDataToRabbitMQ) packDataProp;
                        return msg.travelId().toString().equals("123e4567-e89b-12d3-a456-426614174000") &&
                                msg.studentId().toString().equals("987e6543-e21b-45d3-b321-123456789abc") &&
                                msg.distance().equals(123.5) &&
                                msg.zone().equals("ZONE_A") &&
                                msg.timestamp() != null &&
                                msg.alertType().equals("ZONE_CHANGED");

                    }),
                    any(MessagePostProcessor.class)
            );
        }

        @Test
        @DisplayName("should set deliveryMode message type (QoS 1) with success")
        void shouldSetDeliveryModeMessageTypeWithSuccess() {
            // arrange
            SendPackageDataToRabbitMQ sendPackageData = new SendPackageDataToRabbitMQ(
                    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                    UUID.fromString("987e6543-e21b-45d3-b321-123456789abc"),
                    123.5,
                    "ZONE_A",
                    Instant.now().toString(),
                    "ZONE_CHANGED"
            );

            // act
            notificationService.sendMessage(sendPackageData);

            // assert
            verify(rabbitTemplate).convertAndSend(any(), any(), any(), msgPostProcessorCaptor.capture());
            Message msg = mock(Message.class);
            MessageProperties messageProperties = new MessageProperties();
            when(msg.getMessageProperties()).thenReturn(messageProperties);

            msgPostProcessorCaptor.getValue().postProcessMessage(msg);

            assertEquals(MessageDeliveryMode.PERSISTENT, messageProperties.getDeliveryMode());
        }
    }

    @Nested
    class processFailedMessagesRetryWithParkingLotStrategy {

        @Test
        @DisplayName("should process failed messages with success and send to the parking lot queue")
        void shouldProcessFailedMessagesWithSuccessAndSendToTheParkingLotQueue() {
            // assert
            Message failedMessage = Mockito.mock(Message.class);

            MessageProperties props = new MessageProperties();
            props.getHeaders().put("x-retries-count", 3);

            when(failedMessage.getMessageProperties()).thenReturn(props);

            // act
            notificationService.processFailedMessagesRetryWithParkingLotStrategy(failedMessage);

            // assert
            verify(rabbitTemplate, times(1)).send(
                    eq(RabbitMQConfig.EXCHANGE_PARKING_LOT),
                    eq(RabbitMQConfig.ROUTING_KEY_PARKING_LOT),
                    eq(failedMessage));

            verifyNoMoreInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("should retry messages to the expected queue")
        void shouldRetryingMessagesToTheQueue() {

        }
    }
}