package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.StudentTravelRouteStop;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelCacheDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelRouteStopDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TravelStudentStateCacheService {
    private final Logger log = LoggerFactory.getLogger(TravelStudentStateCacheService.class);

    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> redisOperations;

    private final ObjectMapper objectMapper;

    private final String TRAVEL_STUDENTS_STATE_KEY = "travel:students:state:";

    public TravelStudentStateCacheService(TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.redisTemplate = redisTemplate;
        this.redisOperations = redisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }

    // recupera o estado completo do estudante
    private StudentTravelCacheDTO getCachedState(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[getCachedState] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        String key = TRAVEL_STUDENTS_STATE_KEY + travelId;
        String json = redisOperations.get(key, studentEmail);

        if (json == null) return null;

        try {
            return objectMapper.readValue(json, StudentTravelCacheDTO.class);
        } catch (JsonProcessingException e) {
            // dado corrompido/desatualizado trata como cache-miss, força recarregar do banco
            log.warn("[getCachedState] - falha ao desserializar cache para travelId {} e email {}: {}", travelId, studentEmail, e.getMessage());
            return null;
        }
    }

    // armazena o estado completo do estudante
    private void putCachedState(UUID travelId, String studentEmail, StudentTravelCacheDTO cacheDTO) {
        if (travelId == null || studentEmail == null || cacheDTO == null) {
            log.warn("[putCachedState] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_STATE_KEY + travelId;

        try {
            String json = objectMapper.writeValueAsString(cacheDTO);
            redisOperations.put(key, studentEmail, json);
        } catch (JsonProcessingException e) {
            log.warn("[putCachedState] - falha ao serializar cache para travelId {} e email {}: {}", travelId, studentEmail, e.getMessage());
        }
    }

    // remove o campo individual do estudante
    public void evictStudentTravelCachedData(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[evictStudentTravelCachedData] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_STATE_KEY + travelId;

        redisOperations.delete(key, studentEmail);
    }

    // deleta a hash inteira da viagem de uma vez. IMPORTANTE: *usado em eventos que afetam toda a viagem*
    public void evictStudentsTravel(UUID travelId) {
        if (travelId == null) {
            log.warn("[evictStudentsTravel] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_STATE_KEY + travelId;

        redisTemplate.delete(key);
    }

    // verifica se há cache para retornar direto ou realiza as consultas no banco, armazena e retorna
    public StudentTravelCacheDTO getOrLoadStudentTravelCache(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[getOrLoadStudentTravelCache] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        StudentTravelCacheDTO cached = getCachedState(travelId, studentEmail);

        if (cached != null) {
            return cached;
        }

        StudentTravel studentTravel = studentTravelRepository.findByTravelIdAndStudentEmail(travelId, studentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Student Travel não encontrado para a viagem: " + travelId + " e o email: " + studentEmail));

        StudentTravelCacheDTO freshDTO = new StudentTravelCacheDTO(
                studentTravel.getId(),
                studentTravel.getStudent().getName(),
                studentEmail,
                studentTravel.getStudent().getId(),
                studentTravel.getStudentTravelStatus(),
                studentTravel.isEmbark()
        );

        putCachedState(travelId, studentEmail, freshDTO);

        return freshDTO;
    }

}
