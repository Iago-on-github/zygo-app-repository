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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.shaded.org.checkerframework.checker.guieffect.qual.UI;

import java.util.Objects;
import java.util.UUID;

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

//        when(circuitBreaker.getEventPublisher()).thenReturn(eventPublisher);

        UUID travelId = UUID.randomUUID();

        vehicleGpsMessageDTO = new VehicleGpsMessageDTO(UUID.randomUUID().toString(), travelId.toString(),
                        new VehicleLocationRequestDTO(
                                travelId,
                                -11.231,
                                -38.232,
                                70.3,
                                null
                        )
                );

        routingKey = "v1.gps." + vehicleGpsMessageDTO.city() + "." + vehicleGpsMessageDTO.travelId();
    }

    @Nested
    class sendVehicleGps {

        @Test
        void shouldSendGpsWithSuccess() {
            doAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return null;
            }).when(circuitBreaker).executeRunnable(any(Runnable.class));

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
    }
}