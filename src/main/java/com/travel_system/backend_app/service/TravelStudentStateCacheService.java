package com.travel_system.backend_app.service;

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
    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> redisOperations;

    private final ObjectMapper objectMapper;

    Logger log = LoggerFactory.getLogger(TravelStudentStateCacheService.class);

    private final String TRAVEL_STUDENTS_STATUS_KEY = "travel:students:status:";
    private final String TRAVEL_STUDENTS_EMBARK_KEY = "travel:students:embark:";
    private final String TRAVEL_STUDENTS_ID_KEY = "travel:students:studentId:";
    private final String TRAVEL_STUDENTS_TRAVEL_ID_KEY = "travel:students:studentTravelId:";

    public TravelStudentStateCacheService(TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.redisTemplate = redisTemplate;
        this.redisOperations = redisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }
    // retorna o status armazenado
    private StudentTravelStatus getStudentTravelStatus(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[getStudentTravelStatus] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        String key = TRAVEL_STUDENTS_STATUS_KEY + travelId;

        String status = redisOperations.get(key, studentEmail);

        if (status == null) return null;

        // converte a String do redis no status correspondente
        return StudentTravelStatus.valueOf(status);
    }

    // armazena o status do studentTravel
    private void putStudentTravelStatus(UUID travelId, Map<String, StudentTravelStatus> studentTravelStatuses) {
        if (travelId == null || studentTravelStatuses == null || studentTravelStatuses.isEmpty()) {
            log.warn("[putStudentTravelStatus] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_STATUS_KEY + travelId;

        Map<String, String> convertedMap = studentTravelStatuses.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                map -> map.getValue().toString()
                        ));

        redisOperations.putAll(key, convertedMap);
    }

    // remove o cache do studentTravelStatus
    private void removeStudentTravelStatus(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[removeStudentTravelStatus] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_STATUS_KEY + travelId;

        redisOperations.delete(key, studentEmail);
    }

    // retorna o atual "status" de embarque o estudante [true or false]
    private Boolean getStudentEmbark(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[getStudentEmbark] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        String key = TRAVEL_STUDENTS_EMBARK_KEY + travelId;

        String embark = redisOperations.get(key, studentEmail);

        if (embark == null) return null;

        return Boolean.parseBoolean(embark);
    }

    // armazena o "status" de embarque do estudante
    private void putStudentEmbark(UUID travelId, Map<String, Boolean> studentTravelEmbarks) {
        if (travelId == null || studentTravelEmbarks == null || studentTravelEmbarks.isEmpty()) {
            log.warn("[putStudentEmbark] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_EMBARK_KEY + travelId;

        Map<String, String> mapConverted = studentTravelEmbarks.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        map -> map.getValue().toString()
                ));

        redisOperations.putAll(key, mapConverted);
    }

    // remove o cache do studentTravelEmbark
    private void removeStudentTravelEmbark(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[removeStudentTravelEmbark] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_EMBARK_KEY + travelId;

        redisOperations.delete(key, studentEmail);
    }

    // returna o id do studentId
    private UUID getStudentId(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[getStudentId] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        String key = TRAVEL_STUDENTS_ID_KEY + travelId;

        String stId = redisOperations.get(key, studentEmail);

        if (stId == null) return null;

        return UUID.fromString(stId);
    }

    // armazena o id do student
    private void putStudentId(UUID travelId, Map<String, UUID> studentIds) {
        if (travelId == null || studentIds == null || studentIds.isEmpty()) {
            log.warn("[putStudentId] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_ID_KEY + travelId;

        Map<String, String> mapConverted = studentIds.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        map -> map.getValue().toString()
                ));

        redisOperations.putAll(key, mapConverted);
    }

    // remove o cache do studentId
    private void removeStudentId(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[removeStudentId] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_ID_KEY + travelId;

        redisOperations.delete(key, studentEmail);
    }

    private UUID getStudentTravelId(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[getStudentTravelId] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        String key = TRAVEL_STUDENTS_TRAVEL_ID_KEY + travelId;

        String stId = redisOperations.get(key, studentEmail);

        if (stId == null) return null;

        return UUID.fromString(stId);
    }

    private void putStudentTravelId(UUID travelId, Map<String, UUID> studentTravelsId) {
        if (travelId == null || studentTravelsId == null || studentTravelsId.isEmpty()) {
            log.warn("[putStudentTravelId] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_TRAVEL_ID_KEY + travelId;

        Map<String, String> mapConverted = studentTravelsId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        map -> map.getValue().toString()
                ));

        redisOperations.putAll(key, mapConverted);
    }

    private void removeStudentTravelId(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null ) {
            log.warn("[removeStudentTravelId] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String key = TRAVEL_STUDENTS_TRAVEL_ID_KEY + travelId;

        redisOperations.delete(key, studentEmail);
    }

    // remove o campo individual de cada key correspondente
    public void evictStudentTravelCachedData(UUID travelId, String studentEmail) {
        removeStudentTravelStatus(travelId, studentEmail);
        removeStudentTravelEmbark(travelId, studentEmail);
        removeStudentId(travelId, studentEmail);
        removeStudentTravelId(travelId, studentEmail);
    }

    // deleta todas as keys *usado em eventos que afetam toda a viagem
    public void evictStudentsTravel(UUID travelId) {
        if (travelId == null) {
            log.warn("[evictStudentsTravel] - parâmetros com dados inválidos ou insuficientes");
            return;
        }

        String studentsStatusKey = TRAVEL_STUDENTS_STATUS_KEY + travelId;
        String studentEmbarkKey = TRAVEL_STUDENTS_EMBARK_KEY + travelId;
        String studentIdKey = TRAVEL_STUDENTS_ID_KEY + travelId;
        String studentTravelIdKey = TRAVEL_STUDENTS_TRAVEL_ID_KEY + travelId;

        List<String> keysForEvict = List.of(studentsStatusKey, studentEmbarkKey, studentIdKey, studentTravelIdKey);

        for (String evict : keysForEvict) {
            redisTemplate.delete(evict);
        }
    }

    // orquestrador: verifica se há cache para retornar direto ou realiza as consultas no banco, armazena e retorna
    public StudentTravelCacheDTO getOrLoadStudentTravelCache(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            log.warn("[getOrLoadStudentTravelCache] - parâmetros com dados inválidos ou insuficientes");
            return null;
        }

        UUID studentTravelId = getStudentTravelId(travelId, studentEmail);
        UUID studentId = getStudentId(travelId, studentEmail);
        Boolean studentEmbark = getStudentEmbark(travelId, studentEmail);
        StudentTravelStatus stStatus = getStudentTravelStatus(travelId, studentEmail);

        if (studentTravelId == null || studentId == null || studentEmbark == null || stStatus == null) {
            StudentTravel studentTravel = studentTravelRepository.findByTravelIdAndStudentEmail(travelId, studentEmail)
                    .orElseThrow(EntityNotFoundException::new);

            // busca os campos requeridos p/ cache
            UUID storedStudentTravelId = studentTravel.getId();
            UUID storedStudentId = studentTravel.getStudent().getId();
            boolean embark = studentTravel.isEmbark();
            StudentTravelStatus studentTravelStatus = studentTravel.getStudentTravelStatus();
            List<StudentTravelRouteStop> studentTravelRouteStops = studentTravel.getStudentTravelRouteStops();

            // mapping de cada campo
            Map<String, UUID> mapStudentTravelId = new HashMap<>();
            Map<String, UUID> mapStudentId = new HashMap<>();
            Map<String, Boolean> mapEmbark = new HashMap<>();
            Map<String, StudentTravelStatus> mapStatus = new HashMap<>();
            Map<String, List<StudentTravelRouteStop>> mapRouteStops = new HashMap<>();

            mapStudentTravelId.put(studentEmail, storedStudentTravelId);
            mapStudentId.put(studentEmail, storedStudentId);
            mapEmbark.put(studentEmail, embark);
            mapStatus.put(studentEmail, studentTravelStatus);
            mapRouteStops.put(studentEmail, studentTravelRouteStops);

            // armazena cada campo em seus respectivos métodos dedicados
            putStudentTravelId(travelId, mapStudentTravelId);
            putStudentId(travelId, mapStudentId);
            putStudentEmbark(travelId, mapEmbark);
            putStudentTravelStatus(travelId, mapStatus);

            return new StudentTravelCacheDTO(storedStudentTravelId, studentEmail, storedStudentId ,studentTravelStatus, embark);
        }

        return new StudentTravelCacheDTO(studentTravelId, studentEmail, studentId, stStatus, studentEmbark);
    }

}
