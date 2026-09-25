package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.TravelConstants.TRIP_INACTIVITY_TIMEOUT;

@Service
public class SystemMetricsService {
    private final ThreadPoolTaskExecutor busVelocityNotificationExecutor;
    private final ThreadPoolTaskExecutor vehicleGpsExecutor;
    private final ThreadPoolTaskExecutor studentAwayStateExecutor;
    private final ThreadPoolTaskExecutor sendSensitiveEmailExecutor;
    private final ThreadPoolTaskExecutor routeStopLifecycleExecutor;
    private final ThreadPoolTaskExecutor routeStopApproachExecutor;
    private final ThreadPoolTaskExecutor travelLocationHistoryTaskExecutor;

    private final RedisTrackingService redisTrackingService;
    private final TravelService travelService;

    private final TravelRepository travelRepository;

    private final CircuitBreaker gpsCircuitBreaker;

    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsService.class);

    public SystemMetricsService(@Qualifier("vehicleGpsTaskExecutor") ThreadPoolTaskExecutor vehicleGpsExecutor,
                                @Qualifier("processBusVelocityNotificationType") ThreadPoolTaskExecutor busVelocityNotificationExecutor,
                                @Qualifier("studentAwayTaskExecutor") ThreadPoolTaskExecutor studentAwayStateExecutor,
                                @Qualifier("routeStopTaskExecutor") ThreadPoolTaskExecutor routeStopLifecycleExecutor,
                                @Qualifier("routeStopApproachTaskExecutor") ThreadPoolTaskExecutor routeStopApproachExecutor,
                                @Qualifier("sendSensitiveEmailTaskExecutor") ThreadPoolTaskExecutor sendSensitiveEmailExecutor,
                                @Qualifier("savedTravelLocationTaskExecutor") ThreadPoolTaskExecutor travelLocationHistoryTaskExecutor,
                                RedisTrackingService redisTrackingService, TravelService travelService, TravelRepository travelRepository, CircuitBreakerRegistry registry) {
        this.busVelocityNotificationExecutor = busVelocityNotificationExecutor;
        this.vehicleGpsExecutor = vehicleGpsExecutor;
        this.studentAwayStateExecutor = studentAwayStateExecutor;
        this.sendSensitiveEmailExecutor = sendSensitiveEmailExecutor;
        this.routeStopLifecycleExecutor = routeStopLifecycleExecutor;
        this.routeStopApproachExecutor = routeStopApproachExecutor;
        this.redisTrackingService = redisTrackingService;
        this.travelLocationHistoryTaskExecutor = travelLocationHistoryTaskExecutor;
        this.travelService = travelService;
        this.travelRepository = travelRepository;
        this.gpsCircuitBreaker = registry.circuitBreaker("gpsIngestor");
    }

    @Scheduled(fixedRate = 60000)
    public void getExecutorMetrics() {

        // Executor de Métricas notificações de tracking
        trackingFcmNotificationsMetrics();

        // Executor de Métricas tracking gps
        trackingVehicleGpsMetrics();

        // Executor de Métricas do Circuit Breaker
        circuitBreakerMetrics();

        // Executor de Métricas Travel-Tracking (algoritmo de auto-desvínculo/vínculo_
        studentAwatStateMetrics();

        // Executor de Métricas Send Sensitive Email
        sendSensitiveEmailMetrics();

        // Executor de Métricas RouteStop-Lifecycle-
        routeStopLifecycleMetrics();

        // Executor de Métricas RouteStop-Approach-
        routeStopApproachMetrics();

        // Executor de Métricas: Travel-Location-History-
        travelLocationHistoryMetrics();
    }

    private void trackingFcmNotificationsMetrics() {
        int MAXIMUM_QUEUE_CAPACITY_NOTIFICATION = 15;
        int CORE_POOL_SIZE = 2;

        int notifActiveCount = busVelocityNotificationExecutor.getActiveCount();
        int notifQueueSize   = busVelocityNotificationExecutor.getQueueSize();
        int notifPoolSize    = busVelocityNotificationExecutor.getPoolSize();

        int notifEightyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY_NOTIFICATION, 80);
        int notifFiftyPercent  = percentCalc(MAXIMUM_QUEUE_CAPACITY_NOTIFICATION, 50);

        logger.info("[Executor: FCM-Notification] active: {} | queue: {} | pool: {}",
                notifActiveCount, notifQueueSize, notifPoolSize);

        if (notifQueueSize >= notifEightyPercent) {
            logger.warn("[Executor: FCM-Notification] RED ALERT: fila ultrapassou 80%");
        } else if (notifQueueSize >= notifFiftyPercent) {
            logger.warn("[Executor: FCM-Notification] YELLOW ALERT: fila ultrapassou 50%");
        }

        if (notifPoolSize > CORE_POOL_SIZE) {
            logger.warn("[Executor: FCM-Notification] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void trackingVehicleGpsMetrics() {
        int MAXIMUM_QUEUE_CAPACITY_GPS = 20;
        int CORE_POOL_SIZE = 2;

        // Executor do RabbitMQ GPS (RBMQ-VehicleGps)
        int gpsActiveCount = vehicleGpsExecutor.getActiveCount();
        int gpsQueueSize   = vehicleGpsExecutor.getQueueSize();
        int gpsPoolSize    = vehicleGpsExecutor.getPoolSize();

        int gpsEightyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY_GPS, 80);
        int gpsFiftyPercent  = percentCalc(MAXIMUM_QUEUE_CAPACITY_GPS, 50);

        logger.info("[Executor: RBMQ-VehicleGps] active: {} | queue: {} | pool: {}",
                gpsActiveCount, gpsQueueSize, gpsPoolSize);

        if (gpsQueueSize >= gpsEightyPercent) {
            logger.warn("[Executor: RBMQ-VehicleGps] RED ALERT: fila ultrapassou 80%");
        } else if (gpsQueueSize >= gpsFiftyPercent) {
            logger.warn("[Executor: RBMQ-VehicleGps] YELLOW ALERT: fila ultrapassou 50%");
        }

        if (gpsPoolSize > CORE_POOL_SIZE) {
            logger.warn("[Executor: RBMQ-VehicleGps] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void circuitBreakerMetrics() {
        CircuitBreaker.Metrics metrics = gpsCircuitBreaker.getMetrics();

        float failureRate = metrics.getFailureRate();
        int bufferedCalls = metrics.getNumberOfBufferedCalls();
        int failedCalls = metrics.getNumberOfFailedCalls();
        int successfulCalls = metrics.getNumberOfSuccessfulCalls();
        CircuitBreaker.State state = gpsCircuitBreaker.getState();

        logger.info("[CircuitBreaker metrics] gpsIngestor | estado: {} | taxa de falha: {}% | chamadas: {} (ok: {}, falha: {})",
                state,
                failureRate == -1.0f ? "insuficiente" : String.format("%.1f", failureRate),
                bufferedCalls,
                successfulCalls,
                failedCalls);

        if (failureRate >= 30.0f && failureRate < 50.0f) {
            logger.warn("[CircuitBreaker] gpsIngestor | ALERTA: taxa de falha em {}% — aproximando do limiar de abertura (50%)",
                    String.format("%.1f", failureRate));
        }
    }

    private void studentAwatStateMetrics() {
        int MAXIMUM_QUEUE_CAPACITY = 30;
        int MAXIMUM_POOL_SIZE = 2;

        int studentAwayStateActiveCount = studentAwayStateExecutor.getActiveCount();
        int studentAwayStateQueueSize   = studentAwayStateExecutor.getQueueSize();
        int studentAwayStatePoolSize    = studentAwayStateExecutor.getPoolSize();

        int queueEightyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 80);
        int queueFiftyPercent  = percentCalc(MAXIMUM_QUEUE_CAPACITY, 50);

        logger.info("[Executor: Travel-Presence] active: {} | queue: {} | pool: {} / {}",
                studentAwayStateActiveCount, studentAwayStateQueueSize, studentAwayStatePoolSize, studentAwayStateExecutor.getMaxPoolSize());

        if (studentAwayStateActiveCount >= studentAwayStatePoolSize) {
            logger.warn("[Executor: Travel-Presence] todas as threads estão ocupadas.");
        }

        if (studentAwayStateQueueSize >= queueEightyPercent) {
            logger.warn("[Executor: Travel-Tracking] RED ALERT: fila ultrapassou 80%");
        } else if (studentAwayStateQueueSize >= queueFiftyPercent) {
            logger.warn("[Executor: Travel-Tracking] YELLOW ALERT: fila ultrapassou 50%");
        }

        if (studentAwayStatePoolSize > MAXIMUM_POOL_SIZE) {
            logger.warn("[Executor: Travel-Tracking] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void sendSensitiveEmailMetrics() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int MAXIMUM_POOL_SIZE = 5;

        int sensitiveEmailActiveCount = sendSensitiveEmailExecutor.getActiveCount();
        int sensitiveEmailQueueSize = sendSensitiveEmailExecutor.getQueueSize();
        int sensitiveEmailPoolSize = sendSensitiveEmailExecutor.getPoolSize();

        int queueNinetyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 90);
        int queueFortyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 40);

        logger.info("[Executor: Send-Sensitive-Email] active: {} | queue: {} | pool: {} / {}",
                sensitiveEmailActiveCount, sensitiveEmailQueueSize, sensitiveEmailPoolSize, sendSensitiveEmailExecutor.getMaxPoolSize());

        if (sensitiveEmailActiveCount >= sensitiveEmailPoolSize) {
            logger.warn("[Executor: Send-Sensitive-Email] todas as threads estão ocupadas.");
        }

        if (sensitiveEmailQueueSize >= queueNinetyPercent) {
            logger.warn("[Executor: Send-Sensitive-Email] RED ALERT: fila ultrapassou 90%");
        } else if (sensitiveEmailQueueSize >= queueFortyPercent) {
            logger.warn("[Executor: Send-Sensitive-Email] YELLOW ALERT: fila ultrapassou 40%");
        }

        if (sensitiveEmailPoolSize > MAXIMUM_POOL_SIZE) {
            logger.warn("[Executor: Send-Sensitive-Email] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void routeStopLifecycleMetrics() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int MAXIMUM_POOL_SIZE = 5;

        int routeStopActiveCount = routeStopLifecycleExecutor.getActiveCount();
        int routeStopQueueSize = routeStopLifecycleExecutor.getQueueSize();
        int routeStopPoolSize = routeStopLifecycleExecutor.getPoolSize();

        int queueNinetyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 90);
        int queueFiftyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 50);

        logger.info("[Executor: RouteStop-Lifecycle-] active: {} | queue: {} | pool: {} / {}",
                routeStopActiveCount, routeStopQueueSize, routeStopPoolSize, sendSensitiveEmailExecutor.getMaxPoolSize());


        if (routeStopActiveCount >= routeStopPoolSize) {
            logger.warn("[Executor: RouteStop-Lifecycle-] todas as threads estão ocupadas.");
        }

        if (routeStopQueueSize >= queueNinetyPercent) {
            logger.warn("[Executor: RouteStop-Lifecycle-] RED ALERT: fila ultrapassou 90%");
        } else if (routeStopQueueSize >= queueFiftyPercent) {
            logger.warn("[Executor: RouteStop-Lifecycle-] YELLOW ALERT: fila ultrapassou 40%");
        }

        if (routeStopPoolSize > MAXIMUM_POOL_SIZE) {
            logger.warn("[Executor: RouteStop-Lifecycle-] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void routeStopApproachMetrics() {
        int MAXIMUM_QUEUE_CAPACITY = 50;
        int MAXIMUM_POOL_SIZE = 8;

        int routeStopActiveCount = routeStopApproachExecutor.getActiveCount();
        int routeStopQueueSize = routeStopApproachExecutor.getQueueSize();
        int routeStopPoolSize = routeStopApproachExecutor.getPoolSize();

        int queueNinetyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 90);
        int queueFiftyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 50);

        logger.info("[Executor: RouteStop-Approach-] active: {} | queue: {} | pool: {} / {}",
                routeStopActiveCount, routeStopQueueSize, routeStopPoolSize, sendSensitiveEmailExecutor.getMaxPoolSize());


        if (routeStopActiveCount >= routeStopPoolSize) {
            logger.warn("[Executor: RouteStop-Approach-] todas as threads estão ocupadas.");
        }

        if (routeStopQueueSize >= queueNinetyPercent) {
            logger.warn("[Executor: RouteStop-Approach-] RED ALERT: fila ultrapassou 90%");
        } else if (routeStopQueueSize >= queueFiftyPercent) {
            logger.warn("[Executor: RouteStop-Approach-] YELLOW ALERT: fila ultrapassou 40%");
        }

        if (routeStopPoolSize > MAXIMUM_POOL_SIZE) {
            logger.warn("[Executor: RouteStop-Approach-] poolSize maior que o core. Threads extras criadas.");
        }
    }

    private void travelLocationHistoryMetrics() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int MAXIMUM_POOL_SIZE = 6;

        int activeCount = travelLocationHistoryTaskExecutor.getActiveCount();
        int queueSize = travelLocationHistoryTaskExecutor.getQueueSize();
        int poolSize = travelLocationHistoryTaskExecutor.getPoolSize();

        int queueNinetyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 90);
        int queueFiftyPercent = percentCalc(MAXIMUM_QUEUE_CAPACITY, 50);

        logger.info("[Executor: Travel-Location-History-] active: {} | queue: {} | pool: {} / {}",
                activeCount, queueSize, poolSize, sendSensitiveEmailExecutor.getMaxPoolSize());


        if (activeCount >= poolSize) {
            logger.warn("[Executor: Travel-Location-History-] todas as threads estão ocupadas.");
        }

        if (queueSize >= queueNinetyPercent) {
            logger.warn("[Executor: Travel-Location-History-] RED ALERT: fila ultrapassou 90%");
        } else if (queueSize >= queueFiftyPercent) {
            logger.warn("[Executor: Travel-Location-History-] YELLOW ALERT: fila ultrapassou 40%");
        }

        if (poolSize > MAXIMUM_POOL_SIZE) {
            logger.warn("[Executor: Travel-Location-History-] poolSize maior que o core. Threads extras criadas.");
        }
    }

    // Auto-healing (Detecção de Offline)
    @Scheduled(fixedRate = 180000)
    public void busAutoHealingMonitor() {
        Set<String> allActiveTravelsId = redisTrackingService.getAllActiveTravelsId();

        for (String id : allActiveTravelsId) {
            Long lastPingTimestamp = redisTrackingService.getLastPingTimestamp(UUID.fromString(id));

            if (lastPingTimestamp == null) continue;

            if (isExpired(lastPingTimestamp)) {
                handleTravelTimeout(UUID.fromString(id));
            }
        }

    }

    // encerra a viagem e deleta as telemetrias de cache dessa viagem em específico no redis
    @Transactional
    private void handleTravelTimeout(UUID travelId) {
        // chama service para finalizar a viagem
        travelService.endTravel(travelId);

        redisTrackingService.removeUnactiveTravel(travelId);
        redisTrackingService.clearTravelLocationCache(travelId);

        logger.info("[AUTO-HEALING] Viagem {} encerrada por inatividade.", travelId);
    }

    // verifica se o último ping foi há mais tempo do que o TRIP_INACTIVITY_TIMEOUT configurado
    private boolean isExpired(Long lastPing) {
        return lastPing >= TRIP_INACTIVITY_TIMEOUT;
    }

    private int percentCalc(int original, int percent) {
        return (original * percent) / 100;
    }
}

/*
* GUIDE, uso:
* Usado para avisar antes de um problema virar incidente (fila enchendo, pool saturado, etc.).
* Isso importa mais pra executors que processam volume alto,
* imprevisível, ou crítico pro negócio (tracking, GPS, cooldown) — onde saturação silenciosa causa dano real.
* */