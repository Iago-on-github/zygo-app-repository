package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.constants.CacheConstants;
import com.travel_system.backend_app.events.routestops_algorithm.*;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.CacheConstants.*;
import static com.travel_system.backend_app.config.constants.GlobalAppConstants.*;
import static com.travel_system.backend_app.service.RedisTrackingService.toDoubleOrNull;
import static com.travel_system.backend_app.service.RedisTrackingService.toUUIDOrNull;

/*
* os eventos de mudança de estado e notificações são publicados de forma individual nos respectivos listeners
* roda de forma async no sistema principal de tracking
* os eventos publicados devem ser específicos sobre cada estado, e não de forma genérica
* os eventos que fizerem operações pesadas devem rodar de forma async
*
* */

@Service
public class StudentTravelRouteStopService {
    private final Logger log = LoggerFactory.getLogger(StudentTravelRouteStopService.class);

    private final RouteCalculationService routeCalculationService;
    private final RedisTrackingService redisTrackingService;
    private final TravelTrackingStaticCacheService travelTrackingStaticCacheService;

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    public StudentTravelRouteStopService(RouteCalculationService routeCalculationService, RedisTrackingService redisTrackingService, TravelTrackingStaticCacheService travelTrackingStaticCacheService, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.routeCalculationService = routeCalculationService;
        this.redisTrackingService = redisTrackingService;
        this.travelTrackingStaticCacheService = travelTrackingStaticCacheService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // publica evento quando o estudante não está vinculado a nenhum ponto de parda da rota padrão
    public void validateStudentTravelRouteStop(UUID travelId, UUID studentTravelId, UUID studentId, UUID customerId) {
        if (travelId == null || studentTravelId == null || studentId == null || customerId == null) {
            throw new IllegalArgumentException("Parâmetros requeridos inválidos ou não inseridos");
        }

        Instant lastValidatedAt = Instant.now();

        InvalidStudentTravelRouteStopEvent routeStopEvent = new InvalidStudentTravelRouteStopEvent(studentTravelId, studentId, travelId, customerId, StudentTravelRouteStopStatus.INVALID_ROUTE, lastValidatedAt);

        eventPublisher.publishEvent(routeStopEvent);
    }

    /*
    * inicia o acompanhamento do estudante, encontra o ponto de parada compatível com o período da viagem e publica evento
    * */
    public void initializeStudentTravelRouteStopTracking(UUID travelId, UUID studentTravelId) {
        if (travelId == null || studentTravelId == null) {
            throw new IllegalArgumentException("Parâmetros requeridos inválidos ou não inseridos");
        }

        // recupera os dados de monitoriamento
        StudentTravelRouteStopTrackingCacheDTO trackingData = travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId);

        if (trackingData == null) {
            log.warn("[initializeStudentTravelRouteStopTracking] - tracking data provido do redis null");
            return;
        }

        UUID routeStopId = trackingData.routeStopId();
        Double routeStopLatitude = trackingData.routeStopLatitude();
        Double routeStopLongitude = trackingData.routeStopLongitude();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = StudentTravelRouteStopStatus.EXPECTED;

        InitializeStudentTravelRouteStopEvent event = new InitializeStudentTravelRouteStopEvent(
                studentTravelId,
                travelId,
                routeStopId,
                routeStopLatitude,
                routeStopLongitude,
                studentTravelRouteStopStatus
        );

        eventPublisher.publishEvent(event);

    }

    /*
    * faz o processamento da aproximação do veículo ao ponto de parada do estudante (método principal de tracking)
    * */
    public void processRouteStopApproach(UUID travelId, UUID studentTravelId) {
        if (travelId == null || studentTravelId == null) {
            throw new IllegalArgumentException("Parâmetros requeridos inválidos ou não inseridos");
        }

        /*
         * recupera os campos via pipeline para evitar latência sequencial de rede com o redis
         * */
        String trackingRouteStopsKey = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String trackingField = studentTravelId.toString();

        String routeKey = ROUTE_KEY_PREFIX + travelId;
        String liveTrackingKey = TRACKING_KEY_PREFIX + travelId;

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.opsForHash().get(trackingRouteStopsKey, trackingField); // [0] - trackingData
                operations.opsForHash().entries(routeKey); // [1] - routeData (liveLocation)
                operations.opsForHash().entries(liveTrackingKey); // [2] - trackingData (liveLocation)
                return null;
            }
        });

        StudentTravelRouteStopTrackingCacheDTO trackingData = parseTrackingData(results.get(0));

        if (trackingData == null) {
            log.warn("[processRouteStopApproach] - tracking data provido do redis null");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> routeData = (Map<String, String>) results.get(1);
        @SuppressWarnings("unchecked")
        Map<String, String> liveData = (Map<String, String>) results.get(2);
        LiveLocationDTO lastDriverPosition = parseLiveLocation(routeData, liveData);

        if (lastDriverPosition == null || lastDriverPosition.latitude() == null || lastDriverPosition.longitude() == null) {
            log.warn("[processRouteStopApproach] - dados de localização do motorista não encontrados");
            return;
        }

        DistanceResponseDTO distanceResponseDTO = distanceBetweenVehicleAndRouteStop(travelId, studentTravelId, lastDriverPosition, trackingData);

        if (distanceResponseDTO == null || distanceResponseDTO.distance() == null || distanceResponseDTO.distance() < 0) {
            log.warn("[processRouteStopApproach] - distance retornando null ou inválida");
            return;
        }

        if (distanceResponseDTO.distance() > APPROACHING_THRESHOLD) {
            return;
        }

        if (!trackingData.status().equals(StudentTravelRouteStopStatus.EXPECTED)) {
            return;
        }

        log.info("[processRouteStopApproach] - processamento realizado com sucesso, publicando no listener");

        ProcessStudentTravelRouteStopApproachingEvent processRouteStopApproachingEvent = new ProcessStudentTravelRouteStopApproachingEvent(studentTravelId, trackingData.studentId(), travelId, trackingData.routeStopId(), distanceResponseDTO.distance(), Instant.now());

        eventPublisher.publishEvent(processRouteStopApproachingEvent);
    }

    /*
    * realiza a confirmação de que o estudante chegou ao ponto e desembarcou corretamente
    * */

    public void confirmStudentRouteStopReached(UUID travelId, UUID studentTravelId, StudentTravelStatus studentTravelStatus) {

        /*
         * recupera os campos via pipeline via SessionCallback para evitar latência sequencial no redis
         * */

        String trackingKey = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String trackingField = studentTravelId.toString();

        String monitoringKey = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;
        List<String> monitoringFields = List.of(
                "routeStopId", "studentTravelId", "routeStopLatitude", "routeStopLongitude", "status",
                "distance", "occurredAt", "distanceInMeters", "disembarkAt", "vehiclePositionAt",
                "vehicleLatitude", "vehicleLongitude");

        // pipeline só para as duas chamadas que sempre acontecem juntas, incondicionalmente, no início do fluxo
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForHash().get(trackingKey, trackingField);            // [0]
                operations.opsForHash().multiGet(monitoringKey, monitoringFields);  // [1]
                return null;
            }
        });

        // fase de processamento: só agora os valores reais existem (a fase de emissão acima não retorna nada útil)
        String trackingRaw = (String) results.get(0);
        StudentTravelRouteStopTrackingCacheDTO trackingData = parseTrackingData(trackingRaw);

        if (trackingData == null) {
            log.warn("[confirmStudentRouteStopReached] - tracking data não encontrado");
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> monitoringValues = (List<String>) (List<?>) results.get(1);
        StudentTravelRouteStopsCacheEvent studentTravelRouteStopMonitoring = parseMonitoring(travelId, monitoringValues);

        if (studentTravelRouteStopMonitoring == null) {
            log.warn("[confirmStudentRouteStopReached] - tracking monitoring não encontrado no Redis");
            return;
        }

        StudentTravelRouteStopStatus studentTravelRouteStopStatus = studentTravelRouteStopMonitoring.studentTravelRouteStopStatus();

        // não deve continuar caso o status não seja APPROACHING
        if (!studentTravelRouteStopStatus.equals(StudentTravelRouteStopStatus.APPROACHING)) {
            log.warn("[confirmStudentRouteStopReached] - status retornado do cache não é válido: {}", studentTravelRouteStopStatus);
            return;
        }

        /*
         * faz a validação da evidência de desvínculo com base no status do estudante vinculado à viagem
         * */
        if (!Set.of(StudentTravelStatus.LEFT, StudentTravelStatus.AUTO_DISCONNECTED).contains(studentTravelStatus)) {
            log.warn("[confirmStudentRouteStopReached] - estudante não foi desvinculado ou não saiu da viagem. Status atual: {}", studentTravelStatus);
            return;
        }

        // recupera a última loc estável do veículo — chamada síncrona simples, só ocorre se passou pelas validações acima
        LiveLocationDTO lastDriverPosition = redisTrackingService.getLiveLocation(travelId);

        if (lastDriverPosition == null || lastDriverPosition.latitude() == null || lastDriverPosition.longitude() == null) {
            log.warn("[confirmStudentRouteStopReached] - dados de localização do motorista não encontrados");
            return;
        }

        // calcula distância reaproveitando o trackingData já obtido na pipeline (elimina busca duplicada)
        Double distance = routeCalculationService.calculateHaversineDistanceInMeters(
                trackingData.routeStopLatitude(),
                trackingData.routeStopLongitude(),
                lastDriverPosition.latitude(),
                lastDriverPosition.longitude());

        if (distance == null || distance < 0) {
            log.warn("[confirmStudentRouteStopReached] - distance retornando null ou inválida");
            return;
        }

        // se a distancia nao for compatível retorna
        if (distance > REACHED_THRESHOLD) {
            return;
        }

        ConfirmStudentTravelRouteStopReachedEvent confirmStudentTravelRouteStopReachedEvent = new ConfirmStudentTravelRouteStopReachedEvent(
                studentTravelId,
                trackingData.studentId(),
                travelId,
                trackingData.routeStopId(),
                lastDriverPosition.latitude(),
                lastDriverPosition.longitude(),
                distance,
                Instant.now(),
                lastDriverPosition.current_location_timestamp(),
                studentTravelStatus
        );

        // evento atualização redis
        eventPublisher.publishEvent(confirmStudentTravelRouteStopReachedEvent);

        StudentTravelRouteStopDisembarkedEvent studentTravelRouteStopDisembarkedEvent = new StudentTravelRouteStopDisembarkedEvent(
                studentTravelId,
                trackingData.routeStopId(),
                StudentTravelRouteStopStatus.REACHED,
                Instant.now(),
                lastDriverPosition.current_location_timestamp()
        );

        // evento atualização SQL
        eventPublisher.publishEvent(studentTravelRouteStopDisembarkedEvent);
    }

    /*
    * realiza a confirmação de 'cancelled' caso a viagem seja cancelada
    * */
    public void cancelledStudentRouteStop(UUID travelId, UUID studentTravelId, UUID customerId) {
        if (travelId == null || studentTravelId == null || customerId == null) {
            throw new IllegalArgumentException("Parâmetros requeridos inválidos ou não inseridos");
        }

        // cache estático do tracking
        StudentTravelRouteStopTrackingCacheDTO trackingData = travelTrackingStaticCacheService.getStudentTravelTrackingData(travelId, studentTravelId);

        if (trackingData == null) {
            log.warn("[cancelledStudentRouteStop] - tracking data não encontrado");
            return;
        }

        CancelledStudentTravelRouteStopEvent cancelledEventCache = new CancelledStudentTravelRouteStopEvent(
                studentTravelId,
                trackingData.studentId(),
                trackingData.travelId(),
                trackingData.routeStopId(),
                customerId,
                StudentTravelRouteStopStatus.CANCELLED,
                Instant.now()
        );

        eventPublisher.publishEvent(cancelledEventCache);

    }

    // verifica a distância entre o veículo e o routeStop do aluno
    protected DistanceResponseDTO distanceBetweenVehicleAndRouteStop(UUID travelId, UUID studentTravelId, LiveLocationDTO driverPosition, StudentTravelRouteStopTrackingCacheDTO trackingData) {
        if (trackingData == null) {
            log.warn("[distanceBetweenVehicleAndRouteStop] - cache do contexto não existe mais ou não foi iniciado ainda");
            return null;
        }

        if (driverPosition.latitude() == null || driverPosition.longitude() == null) {
            log.warn("[distanceBetweenVehicleAndRouteStop] - dados de localizaçao do motorista não encontrado");
            return null;
        }

        UUID studentId = trackingData.studentId();
        Double routeStopLatitude = trackingData.routeStopLatitude();
        Double routeStopLongitude = trackingData.routeStopLongitude();

        log.info("studentTravelId {}: Iniciando cálculo de distância entre a viagem e o ponto de parada ", studentTravelId);

        Double distance = routeCalculationService.calculateHaversineDistanceInMeters(
                routeStopLatitude,
                routeStopLongitude,
                driverPosition.latitude(),
                driverPosition.longitude()
        );

        // verifica distância inválida
        if (distance == null || distance < 0) {
            log.warn("[distanceBetweenVehicleAndRouteStop] - distance retornada é inválida");
            return null;
        }

        return new DistanceResponseDTO(studentId, distance);
    }

    protected StudentTravelRouteStopTrackingCacheDTO parseTrackingData(Object raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readValue((String) raw, StudentTravelRouteStopTrackingCacheDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Erro ao desserializar trackingData do JSON", e);
            return null;
        }
    }

    protected StudentTravelRouteStopsCacheEvent parseMonitoring(UUID travelId, List<String> fields) {
        if (fields == null || fields.get(4) == null) return null; // "status" ausente = registro não existe

        return new StudentTravelRouteStopsCacheEvent(
                toUUIDOrNull(fields.get(1)), travelId, toUUIDOrNull(fields.get(0)),
                toDoubleOrNull(fields.get(5)), Instant.parse(fields.get(6)),
                toDoubleOrNull(fields.get(2)), toDoubleOrNull(fields.get(3)),
                StudentTravelRouteStopStatus.valueOf(fields.get(4)),
                toDoubleOrNull(fields.get(7)), Instant.parse(fields.get(8)),
                Instant.parse(fields.get(9)), toDoubleOrNull(fields.get(10)), toDoubleOrNull(fields.get(11)));
    }

    private LiveLocationDTO parseLiveLocation(Map<String, String> routeData, Map<String, String> trackingData) {
        if (routeData == null || trackingData == null) return null;

        String latitude = trackingData.get("current_lat");
        String longitude = trackingData.get("current_lng");
        String geometry = routeData.get("geometry");
        String distance = routeData.get("distanceRemaining");
        String lastCalcLat = routeData.get("last_calc_lat");
        String lastCalcLng = routeData.get("last_calc_lng");
        String timestamp = routeData.get("current_location_timestamp");

        try {
            return new LiveLocationDTO(
                    latitude != null ? Double.parseDouble(latitude) : null,
                    longitude != null ? Double.parseDouble(longitude) : null,
                    geometry,
                    distance != null ? Double.parseDouble(distance) : null,
                    lastCalcLat != null ? Double.parseDouble(lastCalcLat) : null,
                    lastCalcLng != null ? Double.parseDouble(lastCalcLng) : null,
                    timestamp != null ? Instant.parse(timestamp) : null);
        } catch (NumberFormatException e) {
            log.warn("erro ao tentar tratar/retornar dados de localização");
            return null;
        }
    }
}
