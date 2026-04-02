package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpsDataIngestorServiceTest {
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
    private GpsDataIngestorService gpsDataIngestorService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ArgumentCaptor<MessagePostProcessor> messagePostProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

    @Nested
    class gpsDataIngestorService {

        @Test
        @DisplayName("should send vehicle gps to rabbitmq with correct routing key and payload")
        void shouldSendVehicleGpsWithSuccess() {
            // arrange
            UUID travelId = UUID.randomUUID();
            UUID city = UUID.randomUUID();

            String routing_key = "v1.gps." + city + "." + travelId;

            VehicleLocationRequestDTO vehicleRequest = new VehicleLocationRequestDTO(
                    travelId,
                    -12.9714,
                    -38.5014,
                    42.5,
                    180.0
            );

            // act
            gpsDataIngestorService.sendVehicleGps(city.toString(), travelId.toString(), vehicleRequest);

            // assert - captura os argumentos enviados ao rabbitmq
            verify(rabbitTemplate, times(1)).convertAndSend(
                    eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                    eq(routing_key),
                    argThat(payload -> {
                        GpsPayload gps = (GpsPayload) payload;
                        return gps.latitude().equals(vehicleRequest.latitude()) &&
                                gps.longitude().equals(vehicleRequest.longitude()) &&
                                gps.speed().equals(vehicleRequest.speed()) &&
                                gps.heading().equals(vehicleRequest.heading()) &&
                                gps.travelId().equals(vehicleRequest.travelId()) &&
                                gps.cityId().equals(city);
                    }),
                    any(MessagePostProcessor.class)
            );
        }

        @Test
        @DisplayName("should set message post processor type with success")
        void shouldSetMessagePostProcessorTypeWithSuccess() {
            // arrange
            UUID travelId = UUID.randomUUID();
            UUID city = UUID.randomUUID();

            VehicleLocationRequestDTO vehicleRequest = new VehicleLocationRequestDTO(
                    travelId,
                    -12.9714,
                    -38.5014,
                    42.5,
                    180.0
            );

            gpsDataIngestorService.sendVehicleGps(city.toString(), travelId.toString(), vehicleRequest);

            verify(rabbitTemplate).convertAndSend(any(), any(), any(), messagePostProcessorCaptor.capture());

            Message mockMessage = mock(Message.class);
            MessageProperties props = new MessageProperties();
            when(mockMessage.getMessageProperties()).thenReturn(props);

            messagePostProcessorCaptor.getValue().postProcessMessage(mockMessage);

            assertEquals(MessageDeliveryMode.NON_PERSISTENT, props.getDeliveryMode());
        }

        @Test
        @DisplayName("throw exception when city is not a valid UUID")
        void throwExceptionWhenCityIsNotValidUUID() {
            // arrange
            String travelId = UUID.randomUUID().toString();
            String invalidCity = "invalid-city-id";

            VehicleLocationRequestDTO vehicleRequest = new VehicleLocationRequestDTO(
                    UUID.randomUUID(),
                    -12.9714,
                    -38.5014,
                    42.5,
                    180.0
            );

            // act & assert
            assertThrows(IllegalArgumentException.class, () -> gpsDataIngestorService.sendVehicleGps(invalidCity, travelId, vehicleRequest));

            verifyNoInteractions(rabbitTemplate);
        }
    }
}