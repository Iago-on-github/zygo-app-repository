package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GpsDataIngestorServiceTest {
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private CircuitBreaker.EventPublisher eventPublisher;

    private GpsDataIngestorService gpsDataIngestorService;

    private ArgumentCaptor<MessagePostProcessor> messagePostProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

    VehicleGpsMessageDTO vehicleGpsMessageDTO;
    String routingKey;

    @BeforeEach
    void setUp() {
        when(circuitBreakerRegistry.circuitBreaker("gpsIngestor")).thenReturn(circuitBreaker);

        when(circuitBreaker.getEventPublisher()).thenReturn(eventPublisher);

        gpsDataIngestorService = new GpsDataIngestorService(rabbitTemplate, circuitBreakerRegistry);

        // instancia o runner do circuit breaker
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(circuitBreaker).executeRunnable(any(Runnable.class));

        UUID travelId = UUID.randomUUID();

        vehicleGpsMessageDTO = new VehicleGpsMessageDTO(UUID.randomUUID().toString(), travelId.toString(),
                        new VehicleLocationRequestDTO(
                                travelId,
                                -11.231,
                                -38.232,
                                70.3,
                                null));

        routingKey = "v1.gps." + vehicleGpsMessageDTO.city() + "." + vehicleGpsMessageDTO.travelId();
    }

    @Nested
    class sendVehicleGps {

        @Test
        @DisplayName("Deve enviar o gps do rabbitmq com sucesso, usando o circuit breaker encapsulado pelo método running")
        void shouldSendGpsWithSuccess() {
            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, times(1))
                    .convertAndSend(
                            eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                            eq(routingKey),
                            argThat(payload -> {
                                GpsPayload gpsPayload = (GpsPayload) payload;
                                return gpsPayload.travelId().equals(UUID.fromString(vehicleGpsMessageDTO.travelId()))
                                        &&
                                        gpsPayload.cityId().equals(UUID.fromString(vehicleGpsMessageDTO.city()))
                                        &&
                                        gpsPayload.latitude().equals(vehicleGpsMessageDTO.vehicleLocation().latitude())
                                        &&
                                        gpsPayload.longitude().equals(vehicleGpsMessageDTO.vehicleLocation().longitude())
                                        &&
                                        gpsPayload.speed().equals(vehicleGpsMessageDTO.vehicleLocation().speed())
                                        &&
                                        Objects.equals(gpsPayload.heading(), vehicleGpsMessageDTO.vehicleLocation().heading());
                            }),
                            any(MessagePostProcessor.class)
                    );

            verify(circuitBreaker, times(1)).executeRunnable(any(Runnable.class));
        }

        @Test
        @DisplayName("deve descatar a mensagem quando o circuit breaker estiver 'OPEN' ")
        void shouldDiscardGpsMessageWhenCircuitBreakerIsOpen() {
            doThrow(CallNotPermittedException.class).when(circuitBreaker).executeRunnable(any());

            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("falha durante execução - nenhuma exception deve se propagar para o chamador")
        void shouldHandleGenericExceptionDuringGpsSending() {
            rabbitTemplate.setReceiveTimeout(50000); // simula broker extremamente lento

            doThrow(RuntimeException.class).when(circuitBreaker).executeRunnable(any());

            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class doSend {

        @Test
        @DisplayName("Deve criar a routing key e enviar para a exchange correta com sucesso ")
        void shouldBuildRoutingKeyAndSendToCorrectExchange() {
            String buildRoutingKey = "v1.gps." + vehicleGpsMessageDTO.city() + "." + vehicleGpsMessageDTO.travelId();

            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, times(1)).convertAndSend(
                            eq(RabbitMQConfig.EXCHANGE_GPS_NAME),
                            eq(buildRoutingKey),
                            any(),
                            any(MessagePostProcessor.class));
        }

        @Test
        void shouldBuildGpsPayloadCorrectly() {
            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, times(1))
                    .convertAndSend(
                            any(),
                            any(),
                            argThat(payload -> {
                                GpsPayload gpsPayload = (GpsPayload) payload;
                                return gpsPayload.travelId().equals(UUID.fromString(vehicleGpsMessageDTO.travelId()))
                                        &&
                                        gpsPayload.cityId().equals(UUID.fromString(vehicleGpsMessageDTO.city()))
                                        &&
                                        gpsPayload.latitude().equals(vehicleGpsMessageDTO.vehicleLocation().latitude())
                                        &&
                                        gpsPayload.longitude().equals(vehicleGpsMessageDTO.vehicleLocation().longitude())
                                        &&
                                        gpsPayload.speed().equals(vehicleGpsMessageDTO.vehicleLocation().speed())
                                        &&
                                        Objects.equals(gpsPayload.heading(), vehicleGpsMessageDTO.vehicleLocation().heading());
                            }),
                            any(MessagePostProcessor.class)
                    );
        }

        @Test
        @DisplayName("Deve setar a menssagem enviada como 'NON_PERSISTENT' com sucesso.")
        void shouldSetMessageWithNonPersistentWithSuccess() {
            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate).convertAndSend(
                    any(),
                    any(),
                    any(),
                    messagePostProcessorCaptor.capture());

            Message message = new Message(new byte[0], new MessageProperties());

            Message processed = messagePostProcessorCaptor.getValue().postProcessMessage(message);

            assertEquals(MessageDeliveryMode.NON_PERSISTENT, processed.getMessageProperties().getDeliveryMode());
        }

        @ParameterizedTest
        @MethodSource("nullParamsProvider")
        void shouldReturnWhenCityIdOrTravelIdIsNull() {
            gpsDataIngestorService.sendVehicleGps(vehicleGpsMessageDTO);

            verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));
        }

        public static Stream<Arguments> nullParamsProvider() {
            return Stream.of(
                    Arguments.of(UUID.randomUUID(), null),
                    Arguments.of(null, UUID.randomUUID())
            );
        }
    }
}