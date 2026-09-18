package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.constants.CacheConstants;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TravelTrackingStaticCacheService {
    Logger log = LoggerFactory.getLogger(TravelTrackingStaticCacheService.class);

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;

    public TravelTrackingStaticCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /*
    * armazena os dados vinculados ao StudentTravel em cache
    * */
    public void saveStudentTravelTrackingData(StudentTravelRouteStopTrackingCacheDTO cacheDTO) {
        if (cacheDTO == null || cacheDTO.travelId() == null || cacheDTO.studentTravelId() == null) {
            log.warn("[saveStudentTravelTrackingData] - dados de entrada inválidos");
            return;
        }

        try {
            String key = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + cacheDTO.travelId();
            String field = cacheDTO.studentTravelId().toString(); // evita sobreescrita de dados dos estudantes

            // serializa o DTO inteiro para JSON
            String jsonValue = objectMapper.writeValueAsString(cacheDTO);

            redisTemplate.opsForHash().put(key, field, jsonValue);

            log.debug("dados de tracking salvos no cache para studentTravel: {}", cacheDTO.studentTravelId());

        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar cacheDTO para JSON", e);
        }
    }

    // recupera os dados vinculados ao StudentTravel
    public StudentTravelRouteStopTrackingCacheDTO getStudentTravelTrackingData(UUID travelId, UUID studentTravelId) {
        String key = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String field = studentTravelId.toString();

        Object trackingCacheResult = redisTemplate.opsForHash().get(key, field);

        if (trackingCacheResult == null) {
            return null;
        }

        try {
            String trackingCacheString = (String) trackingCacheResult;

            return objectMapper.readValue(trackingCacheString, StudentTravelRouteStopTrackingCacheDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Erro ao desserializar cacheDTO do JSON", e);
            return null;
        }

    }

    // método de evict cache (chamado no encerramento da viagem e/ou no desvínculo do estudante)
    public void removeStudentTravelTrackingCache(UUID travelId, UUID studentTravelId) {
        String key = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String field = studentTravelId.toString();

        redisTemplate.opsForHash().delete(key, field);
    }

}
