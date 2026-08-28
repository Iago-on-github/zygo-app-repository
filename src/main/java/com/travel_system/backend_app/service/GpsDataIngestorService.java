package com.travel_system.backend_app.service;

import com.google.api.client.util.Value;
import com.travel_system.backend_app.config.RabbitMQConfig;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.GpsPayload;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class GpsDataIngestorService {

    private final RabbitTemplate rabbitTemplate;
    private final CircuitBreaker circuitBreaker;

    private final RedisTrackingService redisTrackingService;

    private final Logger logger = LoggerFactory.getLogger(GpsDataIngestorService.class);

    public GpsDataIngestorService(RabbitTemplate rabbitTemplate, CircuitBreakerRegistry registry, RedisTrackingService redisTrackingService) {
        this.rabbitTemplate = rabbitTemplate;
        this.circuitBreaker = registry.circuitBreaker("gpsIngestor");
        this.redisTrackingService = redisTrackingService;

        // registra listener de construção de estado
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> logger.warn("[CircuitBreaker - gpsIngestor] Estado alterado: {} → {}",
                                event.getStateTransition().getToState(),
                                event.getStateTransition().getFromState()));
    }

    // envia ao rabbitmq as informações de gps da viagem + a routing_key contendo ids específicos
    public void sendVehicleGps(VehicleGpsMessageDTO vehicleGpsMessageDTO) {
        try {
            circuitBreaker.executeRunnable(() -> doSend(vehicleGpsMessageDTO));
        } catch (CallNotPermittedException e) {
            // circuit open - broker indisponível
            logger.warn("[sendVehicleGps] Circuito aberto. GPS descartado para a viagem {}. Broker indisponível.",
                    vehicleGpsMessageDTO.travelId());
        } catch (Exception e) {
            // falha real na tentativa de envio (broker lento, timeout, conexão recusada)
            logger.error("[sendVehicleGps] Falha ao enviar GPS para a viagem {}: {}",
                    vehicleGpsMessageDTO.travelId(), e.getMessage());
        }
    }

    private void doSend(VehicleGpsMessageDTO vehicleGpsMessageDTO) {
        if (vehicleGpsMessageDTO.city() == null || vehicleGpsMessageDTO.travelId() == null) {
            logger.warn("[doSend] - dados de parâmetros cityId ou travelId estão null.");
            return;
        }

        final String ROUTING_KEY = "v1.gps." + vehicleGpsMessageDTO.city() + "." + vehicleGpsMessageDTO.travelId();

        Instant now = Instant.now();
        UUID cityId = UUID.fromString(vehicleGpsMessageDTO.city());
        VehicleLocationRequestDTO vehicleLocation = vehicleGpsMessageDTO.vehicleLocation();
        UUID travelId = UUID.fromString(vehicleGpsMessageDTO.travelId());

        /*
        * leitura rápida no redis para preenchimento dos dados de tracking no payload enviado ao rabbitmq
        * */

        // retorna o último ETA armazenado + a distância
        PreviousStateDTO previousEta = redisTrackingService.getPreviousEta(travelId);
        // fornece o último estado do veículo
        AnalyzeMovementStateDTO lastMovementState = redisTrackingService.getLastMovementState(travelId);
        // loc mais recente e o timestamp para o front-end
        LiveLocationDTO liveLocation = redisTrackingService.getLiveLocation(travelId);

        Double durationRemaining = previousEta != null ? previousEta.durationRemaining() : null;
        Double distanceRemaining = previousEta != null ? previousEta.distanceRemaining() : null;
        String movementState = lastMovementState != null ? lastMovementState.movementState().name() : "UNKNOWN";
        Double lastCalcLat = liveLocation != null ? liveLocation.lastCalcLat() : null;
        Double lastCalcLng = liveLocation != null ? liveLocation.lastCalcLng() : null;

        GpsPayload gpsPayload = new GpsPayload(
                vehicleLocation.latitude(),
                vehicleLocation.longitude(),
                vehicleLocation.speed(),
                vehicleLocation.heading(),
                now,
                vehicleLocation.travelId(),
                cityId,
                durationRemaining,
                distanceRemaining,
                movementState,
                lastCalcLat,
                lastCalcLng
        );

        // QoS 0 - não persistente
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_GPS_NAME, ROUTING_KEY, gpsPayload, location -> {
            location.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            return location;
        });
    }
}

