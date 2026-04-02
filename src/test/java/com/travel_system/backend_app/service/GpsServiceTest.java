package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.mensageria.MessagingDTO;
import org.aspectj.weaver.patterns.IVerificationRequired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpsServiceTest {
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
    private GpsService gpsService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ArgumentCaptor<MessagePostProcessor> msgPostProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

    @Nested
    class sendLocalizationToNotification {

        @Test
        @DisplayName("should send localization notification with success")
        void shouldSendLocalizationNotificationWithSuccess() {
            // arrange
            String city = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();

            String routing_gps_key = "v1.gps." + city + "." + travelId;

            MessagingDTO messaging = new MessagingDTO(
                    -12.2597,
                    -38.9647,
                    270.0,
                    35.2,
                    Instant.now(),
                    UUID.randomUUID()
            );

            // act
            gpsService.sendLocalizationToNotification(city, travelId, messaging);

            // assert
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                    eq(routing_gps_key),
                    argThat(msgProp -> {
                        MessagingDTO msg = (MessagingDTO) msgProp;
                        return msg.latitude().equals(-12.2597) &&
                                msg.longitude().equals(-38.9647) &&
                                msg.heading().equals(270.0) &&
                                msg.speed().equals(35.2);
                    }),
                    any(MessagePostProcessor.class)
            );
        }

        @Test
        @DisplayName("should set message post processor type with success")
        void shouldSetMessagePostProcessorTypeWithSuccess() {
            // arrange
            String city = UUID.randomUUID().toString();
            UUID travelId = UUID.randomUUID();

            MessagingDTO messaging = new MessagingDTO(
                    -12.2597,
                    -38.9647,
                    270.0,
                    35.2,
                    Instant.now(),
                    UUID.randomUUID()
            );

            // act
            gpsService.sendLocalizationToNotification(city, travelId, messaging);

            // assert
            verify(rabbitTemplate).convertAndSend(any(), any(), any(), msgPostProcessorCaptor.capture());

            Message mockMessage = mock(Message.class);
            MessageProperties props = new MessageProperties();
            when(mockMessage.getMessageProperties()).thenReturn(props);

            msgPostProcessorCaptor.getValue().postProcessMessage(mockMessage);

            assertEquals(MessageDeliveryMode.NON_PERSISTENT, props.getDeliveryMode());
        }
    }
}