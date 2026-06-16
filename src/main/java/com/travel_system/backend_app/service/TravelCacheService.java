package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.response.TravelCacheDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TravelCacheService {
    private final TravelRepository travelRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> redisOperations;

    private final String STUDENT_TRIP_LIFECYCLE = "trip:student:";
    private final String TRAVEL_STATIC_CACHE = "trip:static:";

    private Logger logger = LoggerFactory.getLogger(TravelCacheService.class);

    public TravelCacheService(TravelRepository travelRepository, RedisTemplate<String, String> redisTemplate) {
        this.travelRepository = travelRepository;
        this.redisTemplate = redisTemplate;
        this.redisOperations = redisTemplate.opsForHash();
    }

    // armazena o cache estático da viagem
    private void storeTravelStaticCache(TravelCacheDTO travelCacheDTO) {
        if (travelCacheDTO == null) {
            logger.warn("[storeTravelStaticCache] - TravelCacheDTO está null.");
            return;
        }

        String key = TRAVEL_STATIC_CACHE + travelCacheDTO.travelId();

        Map<String, String> redisData = new HashMap<>();

        addIfNotNull(redisData, "cityId", travelCacheDTO.cityId());
        addIfNotNull(redisData, "travelStatus", travelCacheDTO.travelStatus());
        addIfNotNull(redisData, "polyline", travelCacheDTO.polylineRoute());
        addIfNotNull(redisData, "finalLatitude", travelCacheDTO.finalLatitude());
        addIfNotNull(redisData, "finalLongitude", travelCacheDTO.finalLongitude());
        addIfNotNull(redisData, "distance", travelCacheDTO.distance());
        addIfNotNull(redisData, "duration", travelCacheDTO.duration());

        if (!redisData.isEmpty()) {
            logger.info("[storeTravelStaticCache] - Dados de cache estático da viagem salvos com sucesso.");
            redisOperations.putAll(key, redisData);
        }
    }

    // recupera o cache estático da viagem
    private TravelCacheDTO getTravelStaticCache(UUID travelId) {
        String key = TRAVEL_STATIC_CACHE + travelId;

        Map<String, String> redisData = redisOperations.entries(key);

        if (redisData.isEmpty()) {
            return null;
        }

        String cityId = redisData.get("cityId");
        String travelStatus = redisData.get("travelStatus");
        String polylineRoute = redisData.get("polyline");
        String finalLatitudeStr = redisData.get("finalLatitude");
        String finalLongitudeStr = redisData.get("finalLongitude");
        String distanceStr = redisData.get("distance");
        String durationStr = redisData.get("duration");

        Double finalLatitude = (finalLatitudeStr != null) ? Double.valueOf(finalLatitudeStr) : null;
        Double finalLongitude = (finalLongitudeStr != null) ? Double.valueOf(finalLongitudeStr) : null;
        Double distance = (distanceStr != null) ? Double.valueOf(distanceStr) : null;
        Double duration = (durationStr != null) ? Double.valueOf(durationStr) : null;

        UUID cityIdConverted = cityId != null ? UUID.fromString(cityId) : null;

        TravelStatus travelStatusConverted = travelStatus != null ? TravelStatus.valueOf(travelStatus) : null;

        return new TravelCacheDTO(travelId, cityIdConverted, travelStatusConverted, finalLatitude, finalLongitude, polylineRoute, distance, duration);
    }

    // invalida (deleta) todo o cache da viagem estática
    public void invalidateTravelStaticCache(UUID travelId) {
        String key = TRAVEL_STATIC_CACHE + travelId;

        redisTemplate.delete(key);
    }

    // orquestrador: verifica se há cache para retornar direto ou realiza as consultas no banco, armazena e retorna
    public TravelCacheDTO getOrLoadTravelStaticCache(UUID travelId) {
        TravelCacheDTO travelStaticCache = getTravelStaticCache(travelId);

        if (travelStaticCache != null) {
            logger.info("[getOrLoadTravelStaticCache] - utilizando dados em cache para a viagem estática.");
            return travelStaticCache;
        } else {
            logger.info("[getOrLoadTravelStaticCache] - sem cache. Buscando e armazenando os dados.");

            Travel travel = travelRepository.findById(travelId)
                    .orElseThrow((() -> new EntityNotFoundException("Viagem " + travelId + " não encontrada.")));

            TravelCacheDTO travelCacheDTO = travelCacheMapper(travel);

            storeTravelStaticCache(travelCacheDTO);

            return travelCacheDTO;
        }
    }

    private TravelCacheDTO travelCacheMapper(Travel travel) {
        return new TravelCacheDTO(
                travel.getId(),
                travel.getCity().getId(),
                travel.getTravelStatus(),
                travel.getFinalLatitude(),
                travel.getFinalLongitude(),
                travel.getPolylineRoute(),
                travel.getDistance(),
                travel.getDuration()
        );
    }

    private void addIfNotNull(Map<String, String> map, String key, Object value) {
        if (value != null) {
            map.put(key, String.valueOf(value));
        }
    }
}
