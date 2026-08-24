package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.ConfirmStudentTravelRouteStopReachedEvent;
import com.travel_system.backend_app.events.InitializeStudentTravelRouteStopEvent;
import com.travel_system.backend_app.events.ProcessStudentTravelRouteStopApproachingEvent;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.events.StudentTravelRouteStopsCacheEvent;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.response.LastLocationDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import io.micrometer.common.util.StringUtils;
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


@Service
public class RedisTrackingService {
    private final RouteCalculationService routeCalculationService;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    private final Logger logger = LoggerFactory.getLogger(RedisTrackingService.class);

    private final String SET_KEY = "ACTIVE_TRAVELS_KEY";

    private final String TRACKING_KEY_PREFIX = "travel:tracking:";
    private final String ROUTE_KEY_PREFIX = "travel:route:";
    private final String STUDENT_TRAVEL_KEY_PREFIX = "travel:away_students:";
    private final String STUDENT_AWAY_STATE_LOCK = "travel:student-away-lock:";
    private final String STUDENT_ROUTE_STOP_MONITORING = "student:route-stop-monitoring:";

    public RedisTrackingService(RouteCalculationService routeCalculationService, RedisTemplate<String, String> redisTemplate) {
        this.routeCalculationService = routeCalculationService;
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    // persiste estado calculado da rota
    public void storeCalculatedRouteState(UUID travelId, String calculationLatitude, String calculationLongitude, RouteDetailsDTO routeDetails) {
        if (travelId == null || calculationLatitude == null || calculationLongitude == null) {
            logger.warn("[storeCalculatedRouteState] dados de cálculo de rota null ou inválidos");
            return;
        }

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        // dados cache
        if (routeDetails.distance() != null) {
            data.put("distanceRemaining", routeDetails.distance().toString());
        } else {
            logger.debug("[storeCalculatedRouteState] distanceRemaining ausente para viagem {}, campo omitido no cache", travelId);
        }

        if (routeDetails.geometry() != null && !routeDetails.geometry().isBlank()) {
            data.put("geometry", routeDetails.geometry());
        } else {
            logger.debug("[storeCalculatedRouteState] geometry ausente para viagem {}, campo omitido no cache", travelId);
        }

        logger.info("[storeCalculatedRouteState] começando tratamento de dados para a viagem: {} ", travelId);

        // ponto de referência de onde a rota foi calculada
        data.put("last_calc_lat", calculationLatitude);
        data.put("last_calc_lng", calculationLongitude);

        hashOperations.putAll(routeKey, data);
    }

    // atualiza o campo "accumulatedDistance"
    public void updateAccumulatedDistance(UUID travelId, Double incrementalDistance) {
        if (travelId == null || incrementalDistance == null) {
            logger.warn("[updateAccumulatedDistance] - dados inválidos");
            return;
        }

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        try {
            double currentAccumulated = Double.parseDouble(getAccumulatedDistance(travelId));

            String updatedAccumulated = String.valueOf(currentAccumulated + incrementalDistance);

            hashOperations.put(routeKey, "accumulatedDistance", updatedAccumulated);

            logger.info("[updateAccumulatedDistance] distância acumulada atualizada para viagem {}: {}", travelId, updatedAccumulated);

        } catch (NumberFormatException e) {
            logger.warn("[updateAccumulatedDistance] valor inválido de accumulatedDistance para viagem {}", travelId);
        }
    }

    // armazena os dados da posição atual do veículo
    public void storeCurrentLocation(UUID travelId, CurrentVehicleLocationDTO currentVehicleLocation) {
        if (travelId == null || currentVehicleLocation.latitude() == null || currentVehicleLocation.longitude() == null) {
            logger.warn("[storeCurrentLocation] dados de localização null ou inválidos");
            return;
        }

        String currentLatitude = currentVehicleLocation.latitude().toString();
        String currentLongitude = currentVehicleLocation.longitude().toString();

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        data.put("current_lat", currentLatitude);
        data.put("current_lng", currentLongitude);

        data.put("current_location_timestamp", String.valueOf(Instant.now().toEpochMilli()));

        if (currentVehicleLocation.speed() != null) data.put("current_speed", String.valueOf(currentVehicleLocation.speed()));

        if (currentVehicleLocation.heading() != null) data.put("current_heading", String.valueOf(currentVehicleLocation.heading()));

        hashOperations.putAll(trackingKey, data);
    }

    // provê a distância acumulada armazeada no redis
    public String getAccumulatedDistance(UUID travelId) {
        if (travelId == null) return null;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        String accumulatedDistance = hashOperations.get(routeKey, "accumulatedDistance");

        return accumulatedDistance != null ? accumulatedDistance : "0.0";
    }

    // retorna a localização atual
    public CurrentVehicleLocationDTO getCurrentLocation(UUID travelId) {
        if (travelId == null) return null;

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        Map<String, String> data = hashOperations.entries(trackingKey);

        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            Double latitude = toDoubleOrNull(data.get("current_lat"));
            Double longitude = toDoubleOrNull(data.get("current_lng"));
            Double speed = toDoubleOrNull(data.get("current_speed"));
            Double heading = toDoubleOrNull(data.get("current_heading"));

            if (latitude == null || longitude == null) {
                logger.info("[getCurrentLocation] - lat/lng retornando null do redis, viagem: {} ", travelId);
                return null;
            }

            return new CurrentVehicleLocationDTO(latitude, longitude, speed, heading);

        } catch (NumberFormatException e) {
            logger.warn("[getCurrentLocation] ocorreu um erro durante o retorno dos dados. Viagem: {} ", travelId);
            return null;
        }
    }

    // retorna os dados de estado calculado da rota
    public Optional<RouteDetailsDTO> getRouteState(UUID travelId) {
        if (travelId == null) return Optional.empty();

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        Map<String, String> data = hashOperations.entries(routeKey);

        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }

        try {
            Double durationRemaining = toDoubleOrNull(data.get("durationRemaining"));
            Double distanceRemaining = toDoubleOrNull(data.get("distanceRemaining"));
            String geometry = data.get("geometry");

            return Optional.of(new RouteDetailsDTO(durationRemaining, distanceRemaining, geometry));
        } catch (NumberFormatException e) {
            // distinguir de "a rota ainda não foi calculada" e "os dados armazenados estão inválidos
            logger.warn("[getRouteState] ocorreu um erro durante o retorno dos dados. Viagem: {} ", travelId);
            return Optional.empty();
        }
    }

    // retorna os dados de estado técnico da rota
    public RouteCalculationReferenceDTO getRouteCalculateReference(UUID travelId) {
        if (travelId == null) return null;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        Map<String, String> data = hashOperations.entries(routeKey);

        if (data == null || data.isEmpty()) return null;

        try {
            Double lastCalcLat = toDoubleOrNull(data.get("last_calc_lat"));
            Double lastCalcLng = toDoubleOrNull(data.get("last_calc_lng"));

            return new RouteCalculationReferenceDTO(lastCalcLat, lastCalcLng);
        } catch (NumberFormatException e) {
            logger.warn("[getRouteCalculateReference] ocorreu um erro durante o retorno dos dados. Viagem: {}", travelId);
            return null;
        }
    }

    // retorna o último ETA armazenado + a distância (provider = updateTripEtaState)
    public PreviousStateDTO getPreviousEta(UUID travelId) {
        if (travelId == null) return null;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        List<String> consultData = hashOperations.multiGet(routeKey, Arrays.asList("durationRemaining", "distanceRemaining", "etaLastUpdatedAt"));

        String durationRemaining = consultData.get(0);
        String distance = consultData.get(1);
        String timestampLastPing = consultData.get(2);

        logger.info("[getPreviousETA] - durationRemaining: {} {} {}", durationRemaining + " distanceRemaining: ", distance + " etaLastUpdatedAt: ", timestampLastPing);

        return new PreviousStateDTO(
                durationRemaining != null ? Double.parseDouble(durationRemaining) : null,
                distance != null ? Double.parseDouble(distance) : null,
                timestampLastPing != null ? Long.parseLong(timestampLastPing) : null);
    }

    // fornece a loc mais recente e o timestamp para o front-end
    public LiveLocationDTO getLiveLocation(UUID travelId) {
        if (travelId == null) return null;

        String routeKey = ROUTE_KEY_PREFIX + travelId;
        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        Map<String, String> routeData = hashOperations.entries(routeKey);
        Map<String, String> trackingData = hashOperations.entries(trackingKey);

        // posição atual do motorista
        String latitude = trackingData.get("current_lat");
        String longitude = trackingData.get("current_lng");

        // dados de rota que ficarão em cache
        String geometry = routeData.get("geometry");
        String distance = routeData.get("distanceRemaining");

        // posição do último cálculo da chamada da api
        String lastCalcLat = routeData.get("last_calc_lat");
        String lastCalcLng = routeData.get("last_calc_lng");

        String current_location_timestamp = routeData.get("current_location_timestamp");

        try {
            return new LiveLocationDTO(
                    latitude != null ? Double.parseDouble(latitude) : null,
                    longitude != null ? Double.parseDouble(longitude) : null,
                    geometry,
                    distance != null ? Double.parseDouble(distance) : null,
                    lastCalcLat != null ? Double.parseDouble(lastCalcLat) : null,
                    lastCalcLng != null ? Double.parseDouble(lastCalcLng) : null,
                    current_location_timestamp != null ? Instant.parse(current_location_timestamp) : null);
        } catch (NumberFormatException e) {
            logger.warn("erro ao tentar tratar/retornar algum dado requerido da viagem: {}", travelId);
            return null;
        }
    }

    // fornece a última loc registrada - estado de localização (antes da loc mais recente)
    public LastLocationDTO getLastLocation(UUID travelId) {
        if (travelId == null) return null;

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        // read hash
        List<String> hashFields = hashOperations.multiGet(trackingKey, Arrays.asList("last_ping_lat", "last_ping_lng", "last_ping_timestamp"));

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
    public AnalyzeMovementStateDTO getLastMovementState(UUID travelId) {
        if (travelId == null) return null;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        List<String> hashFields = hashOperations.multiGet(routeKey, Arrays.asList("movementState", "stateStartedAt", "lastNotificationSendAt", "lastEtaNotificationAt"));

        String cacheMovementState = hashFields.get(0);
        String cacheStateStartedAt = hashFields.get(1);
        String cacheLastNotificationSendAt = hashFields.get(2);
        String cacheLastEtaNotificationAt = hashFields.get(3);

        logger.error("[getLastMovementState] movementState: {}, stateStartedAt: {} ", cacheMovementState, cacheStateStartedAt);

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
    public void storeTravelMetadata(UUID travelId, RouteDetailsDTO routeDetails, String status) {
        if (travelId == null) return;

        String durationRemaining = String.valueOf(routeDetails.duration());
        String distanceRemaining = String.valueOf(routeDetails.distance());

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        // HSET: vai atualizar os campos de distance, eta e status sem afetar LAT/LNG
        Map<String, String> mapToStoredUpdatedData = new HashMap<>();

        mapToStoredUpdatedData.put("durationRemaining", durationRemaining);
        mapToStoredUpdatedData.put("metadataUpdatedAt", String.valueOf(Instant.now().toEpochMilli()));
        mapToStoredUpdatedData.put("distanceRemaining", distanceRemaining);
        mapToStoredUpdatedData.put("status", status);

        hashOperations.putAll(routeKey, mapToStoredUpdatedData);
    }

    // mantém memória entre os pings do driver
    public void keepMemoryBetweenDriverPings(UUID travelId, LiveLocationDTO driverPosition) {
        if (travelId == null) return;

        long now = Instant.now().toEpochMilli();

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        data.put("last_ping_lat", String.valueOf(driverPosition.latitude()));
        data.put("last_ping_lng", String.valueOf(driverPosition.longitude()));
        data.put("lastPingReceivedAt", String.valueOf(now));

        logger.info("[keepMemoryBetweenDriverPings] Com estado, salvando...: {}", travelId);

        hashOperations.putAll(trackingKey, data);
    }

    // atualiza o estado de ETA da viagem
    public void updateTripEtaState(UUID travelId, Double distanceRemaining, Double durationRemaining, Instant timestamp) {
        if (travelId == null) return;

        String distanceRemainingString = String.valueOf(distanceRemaining);
        String durationRemainingToString = String.valueOf(durationRemaining);
        String timestampToString = String.valueOf(timestamp.toEpochMilli());

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        Map<String, String> data = new HashMap<>();

        data.put("distanceRemaining", distanceRemainingString);
        data.put("durationRemaining", durationRemainingToString);
        data.put("etaLastUpdatedAt", timestampToString);

        hashOperations.putAll(routeKey, data);
    }

    // armazena apenas o estado de movimento
    public void saveAnalyzedMovementState(UUID travelId, AnalyzeMovementStateDTO analyzeMovementStateDTO) {
        if (travelId == null) return;

        // primeiro ping
        if (analyzeMovementStateDTO == null) return;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        String movementState = String.valueOf(analyzeMovementStateDTO.movementState());
        String stateStartedAt = String.valueOf(analyzeMovementStateDTO.stateStartedAt());

        Map<String, String> data = new HashMap<>();

        List<String> fieldsHash = hashOperations.multiGet(routeKey, Arrays.asList("movementState", "lastNotificationSendAt", "lastEtaNotificationAt"));

        String cacheMovementState = fieldsHash.get(0);
        String lastNotificationSendAt = fieldsHash.get(1);
        String lastEtaNotificationAt = fieldsHash.get(2);

        if (cacheMovementState == null) {
            logger.info("[saveAnalyzedMovementState] - sem movementState salvo no redis, viagem: {}", travelId);
            velocityAnalysisHelper(routeKey, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        }
        else if (!movementState.equals(cacheMovementState)) {
            logger.info("[saveAnalyzedMovementState] - movementState atual é diferente do salvo no redis, viagem: {}", travelId);
            velocityAnalysisHelper(routeKey, movementState, data, stateStartedAt, lastNotificationSendAt, lastEtaNotificationAt);
        } else {
            logger.info("[saveAnalyzedMovementState] - movementState atual é exatamente o salvo no redis, viagem: {}", travelId);
            data.put("movementState", movementState);
            hashOperations.putAll(routeKey, data);
        }
    }

    // marca que uma notificação foi enviada
    public void markNotificationAsSent(UUID travelId) {
        if (travelId == null) return;

        String routeKey = ROUTE_KEY_PREFIX + travelId;

        String lastNotificationSendAt = String.valueOf(Instant.now());

        hashOperations.put(routeKey, "lastNotificationSendAt", lastNotificationSendAt);
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

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        String timestamp = hashOperations.get(trackingKey, "lastPingReceivedAt");

        return timestamp != null ? Long.parseLong(timestamp) : null;
    }

    // limpa os dados de cache do redis da viagem em específico
    public void clearTravelLocationCache(UUID travelId) {
        if (travelId == null) return;

        String routeKey = ROUTE_KEY_PREFIX + travelId;
        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        redisTemplate.delete(List.of(routeKey, trackingKey));

        redisTemplate.opsForSet().remove(SET_KEY, travelId.toString());
    }

    // armazena o ultimo history ping da viagem
    public void saveHistoryPingLocation(UUID travelId, Instant lastPing) {
        if (travelId == null) return;

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        logger.info("[redis] saveHistoryPingLocation called, saving data... {}", travelId);

        hashOperations.put(trackingKey, "last_ping_history", lastPing.toString());
    }

    // verifica se o último ping da viagem foi salvo há menos de X segundos
    public boolean isLocationUpdateAllowed(UUID travelId) {
        if (travelId == null) return false;

        String trackingKey = TRACKING_KEY_PREFIX + travelId;

        // segundos permitidos
        final int allowedSeconds = 10;
        Instant now = Instant.now();

        String getSavedLastPing = hashOperations.get(trackingKey, "last_ping_history");

        Instant lastPingSave;
        // se primeiro ping, retorna true direto
        if (getSavedLastPing == null) return true;

        lastPingSave = Instant.parse(getSavedLastPing);

        // calcula a diferença e garante que o resultado sempre seja positivo
        Duration differenceBetweenTimes = Duration.between(lastPingSave, now).abs();

        return differenceBetweenTimes.toSeconds() >= allowedSeconds;
    }

    // registra o timeStamp de distância do afastamento do student do onibus
    public void markStudentAsAway(UUID travelId, Map<UUID, Long> studentsToMarkAway) {
        if (travelId == null || studentsToMarkAway.isEmpty()) {
            logger.info("[studentAwayFromBus] dados de entrada inválidos ou insuficientes");
            return;
        }

        String studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + travelId;

        Map<String, String> convertedMap = studentsToMarkAway.entrySet()
                        .stream().collect(Collectors.toMap(
                                e -> e.getKey().toString(),
                                e -> e.getValue().toString()
                ));

        hashOperations.putAll(studentTravelKey, convertedMap);
    }

    // recupera o timstamp do afastamento do student do onibus
    public Map<UUID, Long> getStudentAwayTimestamp(UUID travelId) {
        if (travelId == null) {
            logger.info("[getStudentAwayTimestamp] dados de entrada inválidos ou insuficientes");
            return null;
        }

        String studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + travelId;

        Map<String, String> storedRedis = hashOperations.entries(studentTravelKey);

        Map<UUID, Long> data = new HashMap<>();

        for (Map.Entry<String, String> entry : storedRedis.entrySet()) {
            UUID studentIdKey = UUID.fromString(entry.getKey());

            Long timestampValue = Long.valueOf(entry.getValue());

            data.put(studentIdKey, timestampValue);
        }

        if (data.isEmpty()) return Collections.emptyMap();

        else return data;
    }

    // limpa o registro dos estudantes (não deleta tudo - somente os ids dos estudantes presentes)
    public void clearStudentAwayState(UUID travelId, Set<UUID> studentIds) {
        if (travelId == null || studentIds.isEmpty()) {
            logger.info("[clearStudentAwayState] dados de entrada inválidos ou insuficientes");
            return;
        }

        logger.info("[clearStudentAwayState] - REMOVENDO {} IDS", studentIds.size());

        String studentTravelKey = STUDENT_TRAVEL_KEY_PREFIX + travelId;

        Set<String> studentIdsStr = studentIds.stream().map(UUID::toString).collect(Collectors.toSet());

        String[] arrStudentIds = studentIdsStr.toArray(String[]::new);

        hashOperations.delete(studentTravelKey, arrStudentIds);
    }

    // evita múltiplo processamento do mesmo dado em threads diferentes com base na key
    public boolean tryAcquireStudentAwayStateLock(UUID travelId) {
        String lockKey = STUDENT_AWAY_STATE_LOCK + travelId;

        // tenta criar a chave SOMENTE se ela ainda não existir
        Boolean acquired  = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(30));

        // usa wrapper para evitar problemas com dados null
        return Boolean.TRUE.equals(acquired);
    }

    // deleta a key de LOCK do studentAwayState
    public void releaseStudentAwayStateLock(UUID travelId) {
        String lockKey = STUDENT_AWAY_STATE_LOCK + travelId;

        redisTemplate.delete(lockKey);
    }

    //
    // PONTOS DE PARADA
    //

    // armazena os dados de inicialização do monitoriamento ponto de parada para o estudante (quando o estudante entra na viagem)
    public void storeInitializeStudentTravelRouteStopData(InitializeStudentTravelRouteStopEvent event) {
        if (event.travelId() == null || event.routeStopId() == null || event.studentTravelId() == null) {
            logger.info("[storeInitializeStudentTravelRouteStopData] dados de entrada inválidos ou insuficientes");
            return;
        }

        if (event.routeStopLatitude() == null || event.routeStopLongitude() == null) {
            logger.info("[storeInitializeStudentTravelRouteStopData] dados coordenadas da rota inválidas");
            return;
        }

        String travelId = event.travelId().toString();
        String routeStopId = event.routeStopId().toString();
        String studentTravelId = event.studentTravelId().toString();
        String routeStopLatitude = event.routeStopLatitude().toString();
        String routeStopLongitude = event.routeStopLongitude().toString();

        String status = String.valueOf(event.studentTravelRouteStopStatus());

        String key = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;

        Map<String, String> data = new HashMap<>();

        data.put("travelId", travelId);
        data.put("routeStopId", routeStopId);
        data.put("studentTravelId", studentTravelId);
        data.put("routeStopLatitude", routeStopLatitude);
        data.put("routeStopLongitude", routeStopLongitude);
        data.put("status", status);

        hashOperations.putAll(key, data);
    }

    /*
    * armazena os novos dados do tracking quando ocorre uma transição de estado (ex., "expected" -> "approaching")
    * não carrega o status do DTO, pois o próprio DTO já representa o estado de approaching
    * */
    public void updateStudentTravelRouteStopProcessMonitoring(ProcessStudentTravelRouteStopApproachingEvent event) {
        if (event.travelId() == null || event.routeStopId() == null || event.studentTravelId() == null) {
            logger.info("[updateStudentTravelRouteStopMonitoring] dados de entrada inválidos ou insuficientes");
            return;
        }

        // dados da key
        String studentTravelId = event.studentTravelId().toString();
        String travelId = event.travelId().toString();

        String distance = event.distance().toString();
        String occurredAt = event.occurredAt().toString();

        String key = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;

        Map<String, String> data = new HashMap<>();

        // armazena somente o que não havia ainda
        data.put("distance", distance);
        data.put("occurredAt", occurredAt);
        data.put("status", StudentTravelRouteStopStatus.APPROACHING.toString());

        hashOperations.putAll(key, data);
    }

    /*
    * armazena os novos dados do trackig quando ocorre a transição de estado ("approaching" -> "reached")
    * não carrega o status do DTO, pois o próprio DTO já representa o estado de reached
    * */
    public void updateStudentTravelRouteStopConfirmMonitoring(ConfirmStudentTravelRouteStopReachedEvent event) {
        if (event.travelId() == null || event.routeStopId() == null || event.studentTravelId() == null) {
            logger.info("[updateStudentTravelRouteStopProcess] dados de entrada inválidos ou insuficientes");
            return;
        }

        String travelId = event.travelId().toString();
        UUID studentTravelId = event.studentTravelId();

        String key = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;

        Map<String, String> data = new HashMap<>();

        data.put("status", StudentTravelRouteStopStatus.REACHED.toString());
        data.put("distanceInMeters", event.distanceInMeters().toString());
        data.put("disembarkAt", event.disembarkAt().toString());
        data.put("vehiclePositionAt", event.vehiclePositionAt().toString());
        data.put("vehicleLatitude", event.vehicleLatitude().toString());
        data.put("vehicleLongitude", event.vehicleLongitude().toString());

        hashOperations.putAll(key, data);
    }

    // recupera os dados de monitoriamento do ponto de parada para o estudante
    public StudentTravelRouteStopsCacheEvent getStudentTravelRouteStopMonitoring(UUID travelId, UUID studentTravelId) {
        if (travelId == null) {
            logger.info("[getStudentTravelRouteStopMonitoring] dados de entrada inválidos ou insuficientes");
            return null;
        }

        String key = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;

        List<String> fieldsToGet = Arrays.asList(
                "routeStopId",
                "studentTravelId",
                "routeStopLatitude",
                "routeStopLongitude",
                "status",
                "distance",
                "occurredAt",
                // dados dps que a desconexão via reached acontece \/
                "distanceInMeters",
                "disembarkAt",
                "vehiclePositionAt",
                "vehicleLatitude",
                "vehicleLongitude");

        List<String> hashFields = hashOperations.multiGet(key, fieldsToGet);

        String routeStopId = hashFields.getFirst();
        String studentTravelIdCache = hashFields.get(1);
        String routeStopLatitude = hashFields.get(2);
        String routeStopLongitude = hashFields.get(3);
        String status = hashFields.get(4);
        String distance = hashFields.get(5);
        String occurredAt = hashFields.get(6);
        String distanceInMeters = hashFields.get(7);
        String disembarkAt = hashFields.get(8);
        String vehiclePositionAt = hashFields.get(9);
        String vehicleLatitude = hashFields.get(10);
        String vehicleLongitude = hashFields.get(11);

        StudentTravelRouteStopStatus studentTravelRouteStopStatus = StudentTravelRouteStopStatus.valueOf(status);

        return new StudentTravelRouteStopsCacheEvent(
                toUUIDOrNull(studentTravelIdCache),
                travelId,
                toUUIDOrNull(routeStopId),
                toDoubleOrNull(distance),
                Instant.parse(occurredAt),
                toDoubleOrNull(routeStopLatitude),
                toDoubleOrNull(routeStopLongitude),
                studentTravelRouteStopStatus,
                toDoubleOrNull(distanceInMeters),
                Instant.parse(disembarkAt),
                Instant.parse(vehiclePositionAt),
                toDoubleOrNull(vehicleLatitude),
                toDoubleOrNull(vehicleLongitude)
        );

    }

    // realiza a deleção do estado do redis para aquele estudante em específico
    public void deleteStudentTravelRouteStopMonitoring(UUID travelId, UUID studentTravelId) {
        if (travelId == null) {
            logger.info("[deleteStudentTravelRouteStopMonitoring] dados de entrada inválidos ou insuficientes");
            return;
        }

        String key = STUDENT_ROUTE_STOP_MONITORING + travelId + ":" + studentTravelId;

        redisTemplate.delete(key);
    }

    // MÉTODOS AUXILIARES
    private void velocityAnalysisHelper(String key, String movementState, Map<String, String> data, String stateStartedAt, String lastNotificationSendAt, String lastEtaNotificationAt) {
        data.put("movementState", movementState);
        data.put("stateStartedAt", stateStartedAt);

        if (lastNotificationSendAt != null) data.put("lastNotificationSendAt", lastNotificationSendAt);
        if (lastEtaNotificationAt != null) data.put("lastEtaNotificationAt", lastEtaNotificationAt);

        hashOperations.putAll(key, data);
    }

    private Double toDoubleOrNull(String value) {
        return value == null ? null : Double.parseDouble(value);
    }

    private UUID toUUIDOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
