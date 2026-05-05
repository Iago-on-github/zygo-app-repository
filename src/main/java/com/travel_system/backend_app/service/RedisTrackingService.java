package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.response.LastLocationDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.repository.TravelRepository;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Service
public class RedisTrackingService {

    private final RouteCalculationService routeCalculationService;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    private final Logger logger = LoggerFactory.getLogger(RedisTrackingService.class);

    private final String SET_KEY = "ACTIVE_TRAVELS_KEY";
    private final String HASH_KEY_PREFIX = "travelId:";

    public RedisTrackingService(RouteCalculationService routeCalculationService, RedisTemplate<String, String> redisTemplate) {
        this.routeCalculationService = routeCalculationService;
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    // armazena a localização mais recente do motorista em cache com redisTemplate
    public void storeLiveLocation(String travelId, String latitude, String longitude, Double distance, String geometry) {
        if (travelId == null || latitude == null || longitude == null) {
            logger.warn("[storeLiveLocation] dados de localização null ou inválidos");
            return;
        }

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        // dados cache
        if (distance != null) {
            data.put("distanceRemaining", distance.toString());
        } else {
            logger.debug("[storeLiveLocation] distanceRemaining ausente para viagem {}, campo omitido no cache", travelId);
        }

        if (geometry != null && !geometry.isBlank()) {
            data.put("geometry", geometry);
        } else {
            logger.debug("[storeLiveLocation] geometry ausente para viagem {}, campo omitido no cache", travelId);
        }

        // ponto de referência de onde a rota foi calculada
        data.put("last_calc_lat", latitude);
        data.put("last_calc_lng", longitude);

        // obtem o timestamp real do servidor (ultimo ping)
        String currentTimeStamp = String.valueOf(Instant.now().toEpochMilli());
        data.put("timestamp", currentTimeStamp);

        List<String> fields = Arrays.asList("last_calc_lat", "last_calc_lng", "accumulatedDistance");
        List<String> values = hashOperations.multiGet(key, fields);

        try {
            Map<String, String> oldData = IntStream.range(0, fields.size())
                    .filter(i -> values.get(i) != null).boxed()
                    .collect(Collectors.toMap(
                            fields::get,
                            values::get
                    ));

            double totalUntilNow = distance != null ? distance : 0.0; // assume 0.0 como distance quando for o primero ping
            if (!oldData.isEmpty()) {
                double oldLat = Double.parseDouble(oldData.get("last_calc_lat"));
                double oldLng = Double.parseDouble(oldData.get("last_calc_lng"));

                Double distIncremental = routeCalculationService.calculateHaversineDistanceInMeters(
                        Double.parseDouble(latitude), Double.parseDouble(longitude), oldLat, oldLng);

                double previousAccumulated = Double.parseDouble(oldData.getOrDefault("accumulatedDistance", "0"));
                totalUntilNow = previousAccumulated + distIncremental;
            }

            data.put("accumulatedDistance", String.valueOf(totalUntilNow));

            hashOperations.putAll(key, data);
        } catch (Exception e) {
            logger.warn("[storeLiveLocation] Dados de distância da viagem corrompidos ou inválidos: {}", travelId);
        }
    }

    // provê a distância acumulada armazeada no redis
    public String getAccumulatedDistance(UUID travelId) {
        if (travelId == null) return null;

        String key = HASH_KEY_PREFIX + travelId;

        String accumulatedDistance = hashOperations.get(key, "accumulatedDistance");

        return accumulatedDistance != null ? accumulatedDistance : "0.0";
    }

    // retorna o último ETA armazenado + a distância
    public PreviousStateDTO getPreviousEta(String travelId) {
        if (travelId == null) return null;
        String key = HASH_KEY_PREFIX + travelId;

        List<String> consultData = hashOperations.multiGet(key, Arrays.asList("durationRemaining", "distanceRemaining", "timestamp"));

        String durationRemaining = consultData.get(0);
        String distance = consultData.get(1);
        String timestampLastPing = consultData.get(2);

        logger.info("[getPreviousETA] - durationRemaining: {} {} {}", durationRemaining + " distance: ", distance + " timestampLastPing: ", timestampLastPing );

        return new PreviousStateDTO(
                durationRemaining != null ? Double.parseDouble(durationRemaining) : null,
                distance != null ? Double.parseDouble(distance) : null,
                timestampLastPing != null ? Long.parseLong(timestampLastPing) : null);
    }

    // fornece a loc mais recente e o timestamp para o front-end
    public LiveLocationDTO getLiveLocation(String travelId) {
        if (travelId == null) return null;

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = hashOperations.entries(key);

        // última posição
        String latitude = data.get("lat");
        String longitude = data.get("lng");

        // dados de rota que ficarão em cache
        String geometry = data.get("geometry");
        String distance = data.get("distance");

        // posição do último cálculo da chamada da api
        String lastCalcLat = data.get("last_calc_lat");
        String lastCalcLng = data.get("last_calc_lng");

        try {
            return new LiveLocationDTO(
                    latitude != null ? Double.parseDouble(latitude) : null,
                    longitude != null ? Double.parseDouble(longitude) : null,
                    geometry,
                    distance != null ? Double.parseDouble(distance) : null,
                    lastCalcLat != null ? Double.parseDouble(lastCalcLat) : null,
                    lastCalcLng != null ? Double.parseDouble(lastCalcLng) : null);
        } catch (NumberFormatException e) {
            logger.warn("erro ao tentar tratar/retornar algum dado requerido da viagem: {}", travelId);
            return null;
        }
    }

    // fornece a última loc registrada - estado de localização (antes da loc mais recente)
    public LastLocationDTO getLastLocation(UUID travelId) {
        if (travelId == null) return null;

        String key = HASH_KEY_PREFIX + travelId;

        // read hash
        List<String> hashFields = hashOperations.multiGet(key, Arrays.asList("last_ping_lat", "last_ping_lng", "timestamp"));

        String lastPingLat = hashFields.get(0);
        String lastPingLng = hashFields.get(1);
        String timestamp = hashFields.get(2);

        // is first ping return null. Who calling this method decided create an initial state
        if (timestamp == null) return null;
        else {
            if (lastPingLat == null || lastPingLng == null || timestamp.isEmpty()) {
                // retorna null para tratar como primeiro ping
                logger.info("[getLastLocation] first ping para a viagem {}. Retornando null...", travelId);
                return null;
            }

            try {
                double LastPingLatToDouble = Double.parseDouble(lastPingLat);
                double LastPingLngToDouble = Double.parseDouble(lastPingLng);
                long timestampToLong = Long.parseLong(timestamp);

                logger.info("Dados da última loc registrada retornados com sucesso: {}", travelId);

                return new LastLocationDTO(LastPingLatToDouble, LastPingLngToDouble, timestampToLong);
            } catch (NumberFormatException e) {
                logger.warn("Dados de localização corrompidos no Redis para a viagem: {}", travelId);
                return null;
            }
        }
    }

    // fornece o último estado do veículo
    public AnalyzeMovementStateDTO getLastMovementState(String travelId) {
        if (travelId == null) return null;

        String key = HASH_KEY_PREFIX + travelId;

        List<String> hashFields = hashOperations.multiGet(key, Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt"));

        String cacheMovementState = hashFields.get(0);
        String cacheStateStartedAt = hashFields.get(1);
        String cacheLastNotificationSendAt = hashFields.get(2);
        String cacheLastEtaNotificationAt = hashFields.get(3);

        // if not exists = first ping
        if (cacheMovementState == null || cacheStateStartedAt == null) {
            logger.info("[getLastMovementState] - primeiro contato para a viagem: {} ,retornando direto...", travelId);
            return null;
        } else {
            if (StringUtils.isEmpty(cacheMovementState) || StringUtils.isBlank(cacheMovementState)
                    || StringUtils.isEmpty(cacheStateStartedAt) || StringUtils.isBlank(cacheStateStartedAt)) {
                logger.warn("[getLastMovementState] dados de cache inválidos ou corrompidos para a viagem: {}", travelId);
                return null;
            }

            try {
                return new AnalyzeMovementStateDTO(
                        MovementState.valueOf(cacheMovementState),
                        Instant.parse(cacheStateStartedAt),
                        cacheLastNotificationSendAt == null ? null : Instant.parse(cacheLastNotificationSendAt),
                        cacheLastEtaNotificationAt == null ? null : Instant.parse(cacheLastEtaNotificationAt));
            } catch (NumberFormatException | DateTimeParseException e) {
                logger.warn("[getLastMovementState] dados inválidos ou mal formados para a viagem: {}", travelId);
                return null;
            }
        }
    }

    // atualiza ETA restante, distância restante e o status atualizado
    public void storeTravelMetadata(String travelId, Double durationRemaining, Double distance, String status) {
        if (travelId == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        // HSET: vai atualizar os campos de distance, eta e status sem afetar LAT/LNG
        Map<String, String> mapToStoredUpdatedData = new HashMap<>();

        mapToStoredUpdatedData.put("durationRemaining", durationRemaining.toString());
        mapToStoredUpdatedData.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        mapToStoredUpdatedData.put("distanceRemaining", distance.toString());
        mapToStoredUpdatedData.put("status", status);

        hashOperations.putAll(key, mapToStoredUpdatedData);
    }

    // mantém memória entre os pings do driver
    public void keepMemoryBetweenDriverPings(UUID travelId, LiveLocationDTO driverPosition) {
        if (travelId == null) return;

        long now = Instant.now().toEpochMilli();

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        data.put("last_ping_lat", String.valueOf(driverPosition.latitude()));
        data.put("last_ping_lng", String.valueOf(driverPosition.longitude()));
        data.put("timestamp", String.valueOf(now));

        logger.info("[keepMemoryBetweenDriverPings] Com estado, salvando...: {}", travelId);

        hashOperations.putAll(key, data);
    }

    // atualiza o estado de ETA da viagem
    public void updateTripEtaState(UUID travelId, Double distanceRemaining, Double durationRemaining, Instant timestamp) {
        if (travelId == null) {
            logger.debug("[updateTripEtaState] - viagem não encontrada: {} ", travelId);
            return;
        }

        String distanceRemainingString = String.valueOf(distanceRemaining);
        String durationRemainingToString = String.valueOf(durationRemaining);
        String timestampToString = String.valueOf(timestamp.toEpochMilli());

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        data.put("distanceRemaining", distanceRemainingString);
        data.put("durationRemaining", durationRemainingToString);
        data.put("timestamp", timestampToString);

        hashOperations.putAll(key, data);
    }

    // armazena apenas o estado de movimento
    public void saveAnalyzedMovementState(UUID travelId, AnalyzeMovementStateDTO analyzeMovementStateDTO) {
        if (travelId == null) return;

        // primeiro ping
        if (analyzeMovementStateDTO == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        String movementState = String.valueOf(analyzeMovementStateDTO.movementState());
        String stateStartedAt = String.valueOf(analyzeMovementStateDTO.stateStartedAt());

        Map<String, String> data = new HashMap<>();

        List<String> fieldsHash = hashOperations.multiGet(key, Arrays.asList("movementState", "lastNotificationSendAt", "lastEtaNotificationAt"));

        String cacheMovementState = fieldsHash.get(0);
        String lastNotificationSendAt = fieldsHash.get(1);
        String lastEtaNotificationAt = fieldsHash.get(2);

        if (cacheMovementState == null) {
            logger.info("[saveAnalyzedMovementState] - sem movementState salvo no redis, viagem: {}", travelId);
            velocityAnalysisHelper(key, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        }
        else if (!movementState.equals(cacheMovementState)) {
            logger.info("[saveAnalyzedMovementState] - movementState atual é diferente do salvo no redis, viagem: {}", travelId);
            velocityAnalysisHelper(key, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        } else {
            logger.info("[saveAnalyzedMovementState] - movementState atual é exatamente o salvo no redis, viagem: {}", travelId);
            data.put("movementState", movementState);
            hashOperations.putAll(key, data);
        }
    }

    // marca que uma notificação foi enviada
    public void markNotificationAsSent(String travelId) {
        if (travelId == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        String lastNotificationSendAt = String.valueOf(Instant.now());

        hashOperations.put(key, "lastNotificationSendAt", lastNotificationSendAt);
    }

    // adiciona ids de viagens ativas no set do redis
    public void addActiveTravel(UUID travelId) {
        if (travelId == null) return;
        redisTemplate.opsForSet().add(SET_KEY, travelId.toString());
    }

    // remove ids de viagens inativas do set do redis
    public void removeUnactiveTravel(UUID travelId) {
        if (travelId == null) return;
        redisTemplate.opsForSet().remove(SET_KEY, travelId.toString());
    }

    // retorna os ids de viagens ativas
    public Set<String> getAllActiveTravelsId() {
        return redisTemplate.opsForSet().members(SET_KEY);
    }

    // busca o último momento gravado pelo GPS
    public Long getLastPingTimestamp(UUID travelId) {
        if (travelId == null) return null;

        String key = HASH_KEY_PREFIX + travelId;

        String timestamp = hashOperations.get(key, "timestamp");

        return timestamp != null ? Long.parseLong(timestamp) : null;
    }

    // limpa os dados de cache do redis da viagem em específico
    public void clearTravelLocationCache(UUID travelId) {
        if (travelId == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        redisTemplate.delete(key);

        redisTemplate.opsForSet().remove(SET_KEY, travelId.toString());
    }

    // armazena o ultimo history ping da viagem
    public void saveHistoryPingLocation(UUID travelId, Instant lastPing) {
        if (travelId == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        logger.info("[redis] saveHistoryPingLocation called, saving data... {}", travelId);

        hashOperations.put(key, "last_ping_history", lastPing.toString());
    }

    // verifica se o último ping da viagem foi salvo há menos de X segundos
    public boolean isLocationUpdateAllowed(UUID travelId) {
        if (travelId == null) return false;
        String key = HASH_KEY_PREFIX + travelId;

        // segundos permitidos
        final int allowedSeconds = 10;
        Instant now = Instant.now();

        String getSavedLastPing = hashOperations.get(key, "last_ping_history");

        Instant lastPingSave;
        // se primeiro ping, retorna true direto
        if (getSavedLastPing == null) return true;

        lastPingSave = Instant.parse(getSavedLastPing);

        // calcula a diferença e garante que o resultado sempre seja positivo
        Duration differenceBetweenTimes = Duration.between(lastPingSave, now).abs();

        return differenceBetweenTimes.toSeconds() >= allowedSeconds;
    }

    private void velocityAnalysisHelper(String key, String movementState, Map<String, String> data, String stateStartedAt, String lastNotificationSendAt, String lastEtaNotificationAt) {
        data.put("movementState", movementState);
        data.put("stateStartedAt", stateStartedAt);

        if (lastNotificationSendAt != null) data.put("lastNotificationSendAt", lastNotificationSendAt);
        if (lastEtaNotificationAt != null) data.put("lastEtaNotificationAt", lastEtaNotificationAt);

        hashOperations.putAll(key, data);
    }
}
