package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.config.constants.CacheConstants;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.StudentTravelRouteStop;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelRouteStopDTO;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.travel_system.backend_app.config.constants.CacheConstants;

import java.util.*;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.config.constants.CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY;

@Service
public class TravelTrackingStaticCache {
    Logger log = LoggerFactory.getLogger(TravelTrackingStaticCache.class);

    private final StudentTravelRepository studentTravelRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> redisOperations;

    private final ObjectMapper objectMapper;

    public TravelTrackingStaticCache(RedisTemplate<String, String> redisTemplate, HashOperations<String, String, String> redisOperations, StudentTravelRepository studentTravelRepository, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.redisOperations = redisTemplate.opsForHash();
        this.studentTravelRepository = studentTravelRepository;
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

            redisOperations.put(key, field, jsonValue);

            log.debug("dados de tracking salvos no cache para studentTravel: {}", cacheDTO.studentTravelId());

        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar cacheDTO para JSON", e);
        }
    }

    // recupera os dados vinculados ao StudentTravel
    public StudentTravelRouteStopTrackingCacheDTO getStudentTravelTrackingData(UUID travelId, UUID studentTravelId) {
        String key = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String field = studentTravelId.toString();

        String trackingCacheResult = redisOperations.get(key, field);

        if (trackingCacheResult == null) {
            return null;
        }

        try {
            return objectMapper.readValue(trackingCacheResult, StudentTravelRouteStopTrackingCacheDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Erro ao desserializar cacheDTO do JSON", e);
            return null;
        }

    }

    // método de evict cache (chamado no encerramento da viagem e/ou no desvínculo do estudante)
    public void removeStudentTravelTrackingCache(UUID travelId, UUID studentTravelId) {
        String key = CacheConstants.STUDENT_TRAVEL_ROUTE_STOPS_KEY + travelId;
        String field = studentTravelId.toString();

        redisOperations.delete(key, field);
    }

}
