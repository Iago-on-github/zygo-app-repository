package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.InvalidNotificationStateException;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.NotificationStateDTO;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RedisNotificationService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    private final String HASH_KEY_PREFIX = "notification:";

    private Logger log = LoggerFactory.getLogger(RedisNotificationService.class);

    public RedisNotificationService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    // read
    public NotificationStateDTO readNotificationState(UUID travelId, UUID studentId) {
        if (travelId == null || studentId == null) {
            log.warn("[readNotificationState] travelId: {} ou studentId: {} vindo null, retornando silenciosamente.", travelId, studentId);
            return null;
        }

        String key = HASH_KEY_PREFIX + travelId + ":" + studentId;

        Map<String, String> readData = hashOperations.entries(key);
        readData.get("zone");
        readData.get("lastDistanceNotified");
        readData.get("lastNotificationAt");
        readData.get("timeStamp");

        // ainda sem dados para ler
        if (readData.isEmpty()) {
            log.warn("[readNotificationState] sem dados para leitura para a viagem: {} e o estudante: {}", travelId, studentId);
            return null;
        }

        return new NotificationStateDTO(readData.get("zone"), readData.get("lastDistanceNotified"), readData.get("lastNotificationAt"), readData.get("timeStamp"));
    }

    // verification
    public Boolean verifyNotificationState(UUID travelId, UUID studentId, Double currentDistanceMeters, NotificationStateDTO state) {
        if (state == null || state.zone() == null || state.zone().isEmpty()) {
            log.info("[verifyNotificationState] notificando, state está null ou vazio");
            return true;
        }

        // se nao hoyver ultima notificação confiável, notifica
        if (state.lastNotificationAt() == null || state.lastNotificationAt().isEmpty() || state.lastNotificationAt().isBlank()) {
            log.info("[verifyNotificationState] notificando por falta de última notificação confiável.");
            return true;
        }

        String currentZone;
        double step;

        long timeToNotify = 720000L;
        long elapsedTime = Instant.now().toEpochMilli() - Long.parseLong(state.lastNotificationAt());
        if (currentDistanceMeters >= 1000) {
            currentZone = "FAR";
            step = 200.0;
        } else {
            currentZone = "NEAR";
            step = 30.0;
        }

        if (!currentZone.equals(state.zone())) {
            log.info("[verifyNotificationState] notificando pelo zone atual (currentZone), {}, " +
                    "difere do zone no NotificationStateDTO, {}", currentZone, state.zone());
            return true;
        }

        double lastDistanceNotified = Double.parseDouble(state.lastDistanceNotified());
        double distanceDelta = Math.abs(lastDistanceNotified - currentDistanceMeters);

        if (distanceDelta >= step) {
            log.info("[verifyNotificationState] notificando, distanciaDelta: {} maior ou igual ao step: {}", distanceDelta, step);
            return true;
        }

        // evita spam de notificação caso o onibus fique mt tempo parado (12 min)
        return elapsedTime >= timeToNotify;
    }

    // update
    public void updateNotificationState(UUID travelId, UUID studentId, NotificationStateDTO newState) {
        if (travelId == null || studentId == null) {
            log.warn("[updateNotificationState] travelId: {} ou studentId: {} vindo null, retornando silenciosamente.", travelId, studentId);
            return;
        }

        String key = HASH_KEY_PREFIX + travelId + ":" + studentId;

        Map<String, String> currentState = hashOperations.entries(key);

        if (currentState.isEmpty()) {
            Map<String, String> initialState = new HashMap<>();

            initialState.put("zone", newState.zone());
            initialState.put("lastDistanceNotified", newState.lastDistanceNotified());
            initialState.put("lastNotificationAt", newState.lastNotificationAt());
            String timeStamp = String.valueOf(Instant.now());
            initialState.put("timeStamp", timeStamp);

            hashOperations.putAll(key, initialState);

            log.info("[updateNotificationState] currentState não encontrado no redis, salvando os dados provindos do newState: {}", newState);
            return;
        }

        Map<String, String> fieldsToUpdate = new HashMap<>();

        if (!newState.zone().equals(currentState.get("zone"))) {
            fieldsToUpdate.put("zone", newState.zone());
        }

        if (!newState.lastNotificationAt().equals(currentState.get("lastNotificationAt"))) {
            fieldsToUpdate.put("lastNotificationAt", newState.lastNotificationAt());
        }

        if (newState.lastDistanceNotified().equals(currentState.get("lastDistanceNotified"))) {
            fieldsToUpdate.put("lastDistanceNotified", newState.lastDistanceNotified());
        }

        if (!fieldsToUpdate.isEmpty()) {
            fieldsToUpdate.put("timeStamp", String.valueOf(Instant.now().toEpochMilli()));
            hashOperations.putAll(key, fieldsToUpdate);
        }

    }
}
