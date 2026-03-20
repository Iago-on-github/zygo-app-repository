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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Service
public class RedisTrackingService {

    private final RouteCalculationService routeCalculationService;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;
    private final TravelRepository travelRepository;

    private final Logger logger = LoggerFactory.getLogger(RedisTrackingService.class);

    private final String SET_KEY = "ACTIVE_TRAVELS_KEY";
    private final String HASH_KEY_PREFIX = "travelId:";

    public RedisTrackingService(RouteCalculationService routeCalculationService, RedisTemplate<String, String> redisTemplate, TravelRepository travelRepository) {
        this.routeCalculationService = routeCalculationService;
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
        this.travelRepository = travelRepository;
    }

    // armazena a localização mais recente do motorista em cache com redisTemplate
    public void storeLiveLocation(String travelId, String latitude, String longitude, Double distance, String geometry) {
        if (travelId == null) throw new TripNotFound("Id da viagem não encontrado " + travelId);

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        // dados cache
        data.put("distanceRemaining", distance.toString());
        data.put("geometry", geometry);

        // ponto de referência de onde a rota foi calculada
        data.put("last_calc_lat", latitude);
        data.put("last_calc_lng", longitude);

        // obtem o timestamp real do servidor (ultimo ping)
        String currentTimeStamp = String.valueOf(Instant.now().toEpochMilli());
        data.put("timestamp", currentTimeStamp);

        List<String> fields = Arrays.asList("last_calc_lat", "last_calc_lng", "accumulatedDistance");
        List<String> values = hashOperations.multiGet(key, fields);

        Map<String, String> oldData = IntStream.range(0, fields.size())
                .filter(i -> values.get(0) != null).boxed()
                .collect(Collectors.toMap(
                        fields::get,
                        values::get
                ));

        try {
            double totalUntilNow = 0;
            if (!oldData.isEmpty()) {
                double oldLat = Double.parseDouble(oldData.get("last_calc_lat"));
                double oldLng = Double.parseDouble(oldData.get("last_calc_lng"));

                Double distIncremental = routeCalculationService.calculateHaversineDistanceInMeters(
                        Double.parseDouble(longitude), Double.parseDouble(latitude), oldLat, oldLng);

                double previousAccumulated = Double.parseDouble(oldData.getOrDefault("accumulatedDistance", "0"));
                totalUntilNow = previousAccumulated + distIncremental;

                data.put("accumulatedDistance", String.valueOf(totalUntilNow));
            }

            hashOperations.putAll(key, data);
        } catch (Exception e) {
            logger.warn("Dados de distância da viagem corrompidos ou inválidos: {}", travelId);
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

        String durationRemaining = hashOperations.get(key, "durationRemaining");
        String distance = hashOperations.get(key, "distanceRemaining");
        String timestampLastPing = hashOperations.get(key, "timestamp");

        logger.info("[getPreviousETA] - durationRemaining: {} {} {}", durationRemaining + " distance: ", distance + " timestampLastPing: ", timestampLastPing );

        return new PreviousStateDTO(
                durationRemaining != null ? Double.parseDouble(durationRemaining) : null,
                distance != null ? Double.parseDouble(distance) : null,
                timestampLastPing != null ? Long.parseLong(timestampLastPing) : null);
    }

    // fornece a loc mais recente e o timestamp para o front-end
    public LiveLocationDTO getLiveLocation(String travelId) {
        String key = HASH_KEY_PREFIX + travelId;

        if (travelId == null) throw new TripNotFound("Id da viagem não encontrado " + travelId);

        Map<String, String> data = hashOperations.entries(key);

        if (data == null || data.isEmpty()) {
            return null;
        }

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
                    latitude != null ? Double.parseDouble(latitude) : 0.0,
                    longitude != null ? Double.parseDouble(longitude) : 0.0,
                    geometry,
                    distance != null ? Double.parseDouble(distance) : 0.0,
                    lastCalcLat != null ? Double.parseDouble(lastCalcLat) : null,
                    lastCalcLng != null ? Double.parseDouble(lastCalcLng) : null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // fornece a última loc registrada - estado de localização (antes da loc mais recente)
    public LastLocationDTO getLastLocation(UUID travelId) {
        String key = HASH_KEY_PREFIX + travelId;

        // read hash
        String lastPingLat = hashOperations.get(key, "last_ping_lat");
        String lastPingLng = hashOperations.get(key, "last_ping_lng");
        String timestamp = hashOperations.get(key, "timestamp");

        // is first ping return null. Who calling this method decided create an initial state
        if (timestamp == null) return null;
        else {
            if (lastPingLat == null || lastPingLng == null || timestamp.isEmpty()) {
                // retorna null para tratar como primeiro ping
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
        Travel travel = travelRepository.findById(UUID.fromString(travelId)).orElseThrow(() -> new EntityNotFoundException("Travel não encontrada"));

        String key = HASH_KEY_PREFIX + travel.getId();

        String cacheMovementState = hashOperations.get(key, "movementState");
        String cacheStateStartedAt = hashOperations.get(key, "stateStartedAt");
        String cacheLastNotificationSendAt = hashOperations.get(key, "lastNotificationSendAt");
        String cacheLastEtaNotificationAt = hashOperations.get(key, "lastEtaNotificationAt");

        // if not exists = first ping
        if (cacheMovementState == null || cacheStateStartedAt == null) {
            return null;
        } else {
            if (StringUtils.isEmpty(cacheMovementState) || StringUtils.isBlank(cacheMovementState)
                    || StringUtils.isEmpty(cacheStateStartedAt) || StringUtils.isBlank(cacheStateStartedAt)) {
                throw new InvalidMovementPropertiesException("movementState ou stateStartedAt invalidos ou corrompidos");
            }

            return new AnalyzeMovementStateDTO(
                    MovementState.valueOf(cacheMovementState),
                    Instant.parse(cacheStateStartedAt),
                    cacheLastNotificationSendAt == null ? null : Instant.parse(cacheLastNotificationSendAt),
                    cacheLastEtaNotificationAt == null ? null : Instant.parse(cacheLastEtaNotificationAt));
        }
    }

    // atualiza ETA restante, distância restante e o status atualizado
    public void storeTravelMetadata(String travelId, Double durationRemaining, Double distance, String status) {
        String key = HASH_KEY_PREFIX + travelId;

        if (travelId == null) throw new TripNotFound("Id da viagem não encontrado " + travelId);

        // HSET: vai atualizar os campos de distance, eta e status sem afetar LAT/LNG
        hashOperations.put(key, "durationRemaining", durationRemaining.toString());
        hashOperations.put(key, "timestamp", String.valueOf(Instant.now().toEpochMilli()));
        hashOperations.put(key, "distanceRemaining", distance.toString());
        hashOperations.put(key, "status", status);
    }

    // mantém memória entre os pings do driver
    public void keepMemoryBetweenDriverPings(UUID travelId, LiveLocationDTO driverPosition) {
        long now = Instant.now().toEpochMilli();

        String key = HASH_KEY_PREFIX + travelId;

        Map<String, String> data = hashOperations.entries(key);

        data.put("last_ping_lat", String.valueOf(driverPosition.latitude()));
        data.put("last_ping_lng", String.valueOf(driverPosition.longitude()));
        data.put("timestamp", String.valueOf(now));

        logger.info("[keepMemoryBetweenDriverPings] Com estado, salvando...: {}", travelId);

        hashOperations.putAll(key, data);
    }

    // atualiza o estado de ETA da viagem
    public void updateTripEtaState(UUID travelId, Double distanceRemaining, Double durationRemaining, Instant timestamp) {
        if (travelId == null || distanceRemaining == null || durationRemaining == null || timestamp == null) {
            throw new EtaDataStatesInvalidException("Dados do estado ETA inválidos ou corrompidos");
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
        // primeiro ping
        if (analyzeMovementStateDTO == null) return;

        String key = HASH_KEY_PREFIX + travelId;

        String movementState = String.valueOf(analyzeMovementStateDTO.movementState());
        String stateStartedAt = String.valueOf(analyzeMovementStateDTO.stateStartedAt());

        Map<String, String> data = new HashMap<>();

        String cacheMovementState = hashOperations.get(key, "movementState");
        String lastNotificationSendAt = hashOperations.get(key, "lastNotificationSendAt");
        String lastEtaNotificationAt = hashOperations.get(key, "lastEtaNotificationAt");

        if (cacheMovementState == null) {
            velocityAnalysisHelper(key, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        }
        else if (!movementState.equals(cacheMovementState)) {
            velocityAnalysisHelper(key, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        } else {
            data.put("movementState", movementState);
            hashOperations.putAll(key, data);
        }
    }

    // marca que uma notificação foi enviada
    public void markNotificationAsSent(String travelId) {
        Travel travel = travelRepository.findById(UUID.fromString(travelId))
                .orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada"));

        String key = HASH_KEY_PREFIX + travel.getId();

        String lastNotificationSendAt = String.valueOf(Instant.now());

        hashOperations.put(key, "lastNotificationSendAt", lastNotificationSendAt);

    }

    // adiciona ids de viagens ativas no set do redis
    public void addActiveTravel(UUID travelId) {
        redisTemplate.opsForSet().add(SET_KEY, travelId.toString());
    }

    // remove ids de viagens inativas do set do redis
    public void removeUnactiveTravel(UUID travelId) {
        redisTemplate.opsForSet().remove(SET_KEY, travelId);
    }

    // retorna os ids de viagens ativas
    public Set<String> getAllActiveTravelsId() {
        return redisTemplate.opsForSet().members(SET_KEY);
    }

    // busca o último momento gravado pelo GPS
    public Long getLastPingTimestamp(UUID travelId) {
        String key = HASH_KEY_PREFIX + travelId;

        String timestamp = hashOperations.get(key, "timestamp");

        return timestamp != null? Long.parseLong(timestamp) : null;
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
