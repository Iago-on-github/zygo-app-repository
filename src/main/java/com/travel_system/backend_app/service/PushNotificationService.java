package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentProximityEvents;
import com.travel_system.backend_app.events.VehicleMovementEvents;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.VelocityAnalysisDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.PreviousStateDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.LastLocationDTO;
import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.enums.MovementState;
import com.travel_system.backend_app.model.enums.ShouldNotify;
import com.travel_system.backend_app.repository.TravelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PushNotificationService {
    private final TravelService travelService;
    private final RouteCalculationService routeCalculationService;
    private final RedisNotificationService redisNotificationService;
    private final RedisTrackingService redisTrackingService;

    private final ApplicationEventPublisher eventPublisher;

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    public PushNotificationService(TravelService travelService, RouteCalculationService routeCalculationService, RedisNotificationService redisNotificationService, RedisTrackingService redisTrackingService, ApplicationEventPublisher eventPublisher) {
        this.travelService = travelService;
        this.routeCalculationService = routeCalculationService;
        this.redisNotificationService = redisNotificationService;
        this.redisTrackingService = redisTrackingService;
        this.eventPublisher = eventPublisher;
    }

    /*
      gera pushs de notificações por distância <aluno - ônibus>
      ex.: Ônibus está há 200M de você
    */
    public void checkProximityAlerts(VehicleLocationRequestDTO vehicleLocationRequest) {
        UUID travelId = vehicleLocationRequest.travelId();
        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        LiveLocationDTO driverPosition = new LiveLocationDTO(latitude, longitude, null, 0.0, null, null);
        Set<StudentTravelResponseDTO> linkedStudentTravel = travelService.linkedStudentTravel(travelId);
        List<DistanceResponseDTO> differencePosition = distanceBetweenPositions(travelId, driverPosition);

        Map<UUID, Double> distances = differencePosition.stream()
                .collect(Collectors.toMap(DistanceResponseDTO::studentId, DistanceResponseDTO::distance));

        linkedStudentTravel.forEach(student -> {
            NotificationStateDTO readNotificationState = redisNotificationService.readNotificationState(travelId, student.studentId());

            Double distance = distances.get(student.studentId());
            if (distance == null) return; // Segurança caso o aluno não tenha distância calculada

            String zone = distance >= 1000 ? "FAR" : "NEAR";
            String nowMillis = String.valueOf(Instant.now().toEpochMilli());
            String timestamp = String.valueOf(Instant.now());

            Boolean shouldPushNotification = redisNotificationService.verifyNotificationState(
                    travelId,
                    student.studentId(),
                    distance,
                    readNotificationState);

            String alertType = "INITIAL_STATE";

            if (readNotificationState != null && readNotificationState.zone() != null) {
                // Se a zona mudou
                if (!zone.equals(readNotificationState.zone())) {
                    alertType = "ZONE_CHANGED";
                } else {
                    // Se a zona é a mesma, verifica-se o tempo ou distância percorrida
                    try {
                        long lastTime = Long.parseLong(readNotificationState.lastNotificationAt());
                        long elapsedMillis = Instant.now().toEpochMilli() - lastTime;

                        if (elapsedMillis >= 720000) { // 12 minutos
                            alertType = "TIME_ELAPSED";
                        } else {
                            double lastDistance = Double.parseDouble(readNotificationState.lastDistanceNotified());
                            double deltaDistance = Math.abs(distance - lastDistance);
                            double step = zone.equals("FAR") ? 200.0 : 30.0;

                            if (deltaDistance >= step) {
                                alertType = "DISTANCE_STEP_REACHED";
                            } else {
                                alertType = "PERIODIC_UPDATE"; // Caso passe no verify mas não mude zona/step
                            }
                        }
                    } catch (Exception e) {
                        // caso os dados do redis estejam corrompidos, reseta para um estado limpo e retorna sem notificar
                        alertType = "STATE_RECOVERY";
                        logger.warn("[checkProximityAlerts] Dado corrompido no Redis para aluno {} na viagem {}. Resetando estado.", student.studentId(), travelId);

                        redisNotificationService.updateNotificationState(travelId, student.studentId(), new NotificationStateDTO(zone, distance.toString(), nowMillis, timestamp));
                        return;
                    }
                }
            }

            if (shouldPushNotification) {
                eventPublisher.publishEvent(new StudentProximityEvents(
                        travelId,
                        student.studentId(),
                        distance,
                        zone,
                        timestamp,
                        alertType));

                logger.info("Evento publicado [{}]: aluno {} na viagem {}", alertType, student.studentId(), travelId);

                redisNotificationService.updateNotificationState(travelId, student.studentId(),
                        new NotificationStateDTO(zone,
                                distance.toString(),
                                nowMillis,
                                timestamp));
            }
        });
    }

    public void processVehicleMovement(VehicleLocationRequestDTO vehicleLocationRequest) {
        UUID traceId = UUID.randomUUID();

        UUID travelId = vehicleLocationRequest.travelId();
        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        logger.info("[Trace: {}] Iniciando processamento para viagem: {}", traceId, travelId);
        VelocityAnalysisDTO velocityAnalysis = analyzeVehicleMovement(new VehicleLocationRequestDTO(
                travelId,
                latitude,
                longitude,
                speed,
                heading));

        ShouldNotify decision = shouldSendNotification(travelId, velocityAnalysis, traceId);

        // chama para notificação via event
        eventPublisher.publishEvent(new VehicleMovementEvents(
                travelId,
                velocityAnalysis,
                decision,
                traceId));
    }

    // usa analyzeVehicleMovement e decide se deve notificar
    private ShouldNotify shouldSendNotification(UUID travelId, VelocityAnalysisDTO velocityAnalysis, UUID traceId) {
        // verificar mudanças de estado

        AnalyzeMovementStateDTO lastMovementState = redisTrackingService.getLastMovementState(String.valueOf(travelId));
        MovementState actualMovementState = velocityAnalysis.movementState();

        Instant lastEtaNotifyAt = (lastMovementState != null) ? lastMovementState.lastEtaNotificationAt() : null;
        Instant lastGeneralNotifySendAt = (lastMovementState != null) ? lastMovementState.lastNotificationSendAt() : null;

        Instant now = Instant.now();

        // primeiro ciclo: sem estado anterior - seta posição atual como a antiga position registrada
        MovementState movementState;
        if (lastMovementState == null || lastMovementState.movementState() == null) {
            logger.info("[Trace: {}] Primeiro ciclo: Inicializando estado no Redis", traceId);
            redisTrackingService.saveAnalyzedMovementState(travelId, new AnalyzeMovementStateDTO(actualMovementState, now, null, null));
            return ShouldNotify.SHOULD_NO_NOTIFY;
        } else {
            movementState = lastMovementState.movementState();
        }
        logger.info("DEBUG: Estado no Redis: {} | Estado Atual: {}", movementState, actualMovementState);

        final long STATE_TIME_LIMIT_MS = 4_000;
        final long NOTIFICATION_COOLDOWN_MS = 12_000;
        final long NOTIFICATION_COOLDOWN_MS_STOPPED = 300_000;

        // comparar estados
        // se o estado mudou, ainda nao notifica mas salva a mudança no Redis
        if (!actualMovementState.equals(movementState)) {
            logger.info("Estado mudou, ainda não notifica e salva o estado no Redis");
            redisTrackingService.saveAnalyzedMovementState(travelId, new AnalyzeMovementStateDTO(
                    actualMovementState,
                    now,
                    lastGeneralNotifySendAt,
                    lastEtaNotifyAt));
            return ShouldNotify.SHOULD_NO_NOTIFY;
        }

        if (actualMovementState.equals(MovementState.NORMAL)) {
            logger.info("Estado não mudou, não notifica");
            redisTrackingService.saveAnalyzedMovementState(travelId, new AnalyzeMovementStateDTO(
                    actualMovementState,
                    now,
                    lastGeneralNotifySendAt,
                    lastEtaNotifyAt));
            return ShouldNotify.SHOULD_NO_NOTIFY;
        }

        long durationOnState = now.toEpochMilli() - (lastMovementState != null ? lastMovementState.stateStartedAt().toEpochMilli() : now.toEpochMilli());

        boolean cooldownExpired = lastEtaNotifyAt == null || hasEnoughCooldownForStopped(lastEtaNotifyAt, now, NOTIFICATION_COOLDOWN_MS);
        boolean stayedLongEnough = durationOnState >= STATE_TIME_LIMIT_MS;

        Instant stateStartedAt = (lastMovementState != null) ? lastMovementState.stateStartedAt() : null;

        if (stayedLongEnough && cooldownExpired) {
            if (actualMovementState.equals(MovementState.SLOW)) {
                logger.info("[Trace: {}] Decisão: SHOULD_NOTIFY_SLOW", traceId);
                redisTrackingService.saveAnalyzedMovementState(travelId, new AnalyzeMovementStateDTO(actualMovementState, stateStartedAt, now, now));
                return ShouldNotify.SHOULD_NOTIFY_SLOW;
            }
            if (actualMovementState.equals(MovementState.STOPPED) && hasEnoughCooldownForStopped(lastEtaNotifyAt, now, NOTIFICATION_COOLDOWN_MS_STOPPED)) {
                logger.info("[Trace: {}] Decisão: NOTIFY_STOPPED", traceId);
                redisTrackingService.saveAnalyzedMovementState(travelId, new AnalyzeMovementStateDTO(actualMovementState, stateStartedAt, now, now));
                return ShouldNotify.SHOULD_NOTIFY_STOPPED;
            }
        }
        logger.info("[Trace: {}] Decisão: NO_NOTIFY (Motivo: Cooldown/Tempo de estado não atingido)", traceId);
        return ShouldNotify.SHOULD_NO_NOTIFY;
    }

    /*
    gera pushs de notificações por anomalias (detector de problemas) <aluno - ônibus>
    ex.: Ônibus está há 12 minutos parado
    */
    private VelocityAnalysisDTO analyzeVehicleMovement(VehicleLocationRequestDTO vehicleLocationRequest) {
        UUID travelId = vehicleLocationRequest.travelId();
        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();

        LiveLocationDTO lastRecentPosition = redisTrackingService.getLiveLocation(String.valueOf(travelId));
        LastLocationDTO lastLocation = redisTrackingService.getLastLocation(travelId);
        LiveLocationDTO actuallyPosition = getLiveLocationDTO(latitude, longitude, lastRecentPosition);

        VelocityAnalysisDTO result;

        // Primeiro ping
        if (lastLocation == null) {
            return new VelocityAnalysisDTO(null, null, null, null, MovementState.INSUFFICIENT_DATA);
        }

        long elapsedSeconds = Duration
                .between(Instant.ofEpochMilli(lastLocation.timestamp()), Instant.now())
                .toSeconds();

        final int MIN_SECONDS = 5;
        if (elapsedSeconds < MIN_SECONDS) {
            return new VelocityAnalysisDTO(null, null, null, null, MovementState.INSUFFICIENT_DATA);
        }

        // atualiza última posição no redis mesmo se algo falhar
        redisTrackingService.keepMemoryBetweenDriverPings(travelId, actuallyPosition);

        Double distanceBetweenPings =
                routeCalculationService.calculateHaversineDistanceInMeters(
                        longitude, latitude,
                        lastLocation.longitude(),
                        lastLocation.latitude());

        PreviousStateDTO previousEta =
                redisTrackingService.getPreviousEta(String.valueOf(travelId));

        Double newETA = null;
        double distanceRemaining = 0;
        MovementState state;
        double avgSpeed = distanceBetweenPings / elapsedSeconds;
        final double MIN_SPEED_THRESHOLD = 0.5;
        final int MIN_SOLID_SPEED_DISTANCE = 1;

        if (previousEta != null) {
            distanceRemaining = previousEta.distanceRemaining();
        }

        logger.info("[ANALYSIS DEBUG] Travel: {} | Elapsed: {}s | Distance: {}m | Speed: {}m/s | Threshold: {}m/s | Lat/Lng: {} , {}",
                travelId, elapsedSeconds, String.format("%.2f", distanceBetweenPings), String.format("%.2f", avgSpeed), MIN_SPEED_THRESHOLD, latitude, longitude);

        // moveu menos q 1m = está parado
        if (distanceBetweenPings < MIN_SOLID_SPEED_DISTANCE) {
            state = MovementState.STOPPED;
        } else if (avgSpeed <= MIN_SPEED_THRESHOLD) {
            state = MovementState.SLOW;
        } else {
            state = MovementState.NORMAL;
            if (distanceRemaining > 0 && avgSpeed > MIN_SPEED_THRESHOLD) {
                newETA = distanceRemaining / avgSpeed;

                redisTrackingService.updateTripEtaState(
                        travelId,
                        distanceRemaining,
                        newETA,
                        Instant.now()
                );
            }
        }

        result = new VelocityAnalysisDTO(
                avgSpeed,
                elapsedSeconds,
                distanceBetweenPings,
                newETA,
                state
        );

        return result;
    }

    private LiveLocationDTO getLiveLocationDTO(Double latitude, Double longitude, LiveLocationDTO lastRecentPosition) {
        String geometry = lastRecentPosition != null ? lastRecentPosition.geometry() : null;
        double distance = lastRecentPosition != null ? lastRecentPosition.distance() : 0.0;
        double lastCalcLat = lastRecentPosition != null ? lastRecentPosition.lastCalcLat() : 0.0;
        double lastCalcLng = lastRecentPosition != null ? lastRecentPosition.lastCalcLng() : 0.0;

        return new LiveLocationDTO(
                latitude,
                longitude,
                geometry,
                distance,
                lastCalcLat,
                lastCalcLng);
    }

    // distance between driver and student
    protected List<DistanceResponseDTO> distanceBetweenPositions(UUID travelId, LiveLocationDTO driverPosition) {
        Set<StudentTravelResponseDTO> linkedStudentTravel = travelService.linkedStudentTravel(travelId);

        logger.info("Viagem {}: Iniciando cálculo de distância para {} alunos vinculados.", travelId, linkedStudentTravel.size());

        List<DistanceResponseDTO> results = linkedStudentTravel.stream()
                .filter(student -> {
                    boolean hasPosition = student.position() != null;
                    if (!hasPosition) {
                        logger.warn("Aluno {} ignorado: Posição (GeoPosition) está nula no banco.", student.studentId());
                    }
                    return hasPosition;
                })
                .map(student -> {
                    double distance = routeCalculationService.calculateHaversineDistanceInMeters(
                            driverPosition.latitude(),
                            driverPosition.longitude(),
                            student.position().getLatitude(),
                            student.position().getLongitude()
                    );
                    return new DistanceResponseDTO(student.studentId(), distance);
                })
                .toList();
        logger.info("Viagem {}: Cálculo concluído. {} alunos processados com sucesso.", travelId, results.size());
        return results;
    }

    private boolean hasEnoughCooldownForStopped(Instant lastEtaNotify, Instant now, long notificationCooldown) {
        if (lastEtaNotify == null) return true;
        return Duration.between(lastEtaNotify, now).toMillis() >= notificationCooldown;
    }
}
