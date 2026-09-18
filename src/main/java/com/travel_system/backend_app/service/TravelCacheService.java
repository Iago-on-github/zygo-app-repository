package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.CustomerRepository;
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

import static com.travel_system.backend_app.config.constants.CacheConstants.TRAVEL_STATIC_CACHE;

@Service
public class TravelCacheService {
    private final TravelRepository travelRepository;
    private final CustomerRepository customerRepository;

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> redisOperations;

    private Logger logger = LoggerFactory.getLogger(TravelCacheService.class);

    public TravelCacheService(TravelRepository travelRepository, CustomerRepository customerRepository, RedisTemplate<String, String> redisTemplate) {
        this.travelRepository = travelRepository;
        this.customerRepository = customerRepository;
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

        addIfNotNull(redisData, "travelStatus", travelCacheDTO.travelStatus());
        addIfNotNull(redisData, "polyline", travelCacheDTO.polylineRoute());
        addIfNotNull(redisData, "finalLatitude", travelCacheDTO.finalLatitude());
        addIfNotNull(redisData, "finalLongitude", travelCacheDTO.finalLongitude());
        addIfNotNull(redisData, "distance", travelCacheDTO.distance());
        addIfNotNull(redisData, "duration", travelCacheDTO.duration());
        addIfNotNull(redisData, "customerId", travelCacheDTO.customerId());
        addIfNotNull(redisData, "cityId", travelCacheDTO.cityId());

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

        String travelStatus = redisData.get("travelStatus");
        String customerIdStr = redisData.get("customerId");
        String cityIdStr = redisData.get("cityId");
        String polylineRoute = redisData.get("polyline");
        String finalLatitudeStr = redisData.get("finalLatitude");
        String finalLongitudeStr = redisData.get("finalLongitude");
        String distanceStr = redisData.get("distance");
        String durationStr = redisData.get("duration");

        Double finalLatitude = (finalLatitudeStr != null) ? Double.valueOf(finalLatitudeStr) : null;
        Double finalLongitude = (finalLongitudeStr != null) ? Double.valueOf(finalLongitudeStr) : null;
        Double distance = (distanceStr != null) ? Double.valueOf(distanceStr) : null;
        Double duration = (durationStr != null) ? Double.valueOf(durationStr) : null;

        UUID customerId = (customerIdStr != null) ? UUID.fromString(customerIdStr) : null;
        UUID cityId = (cityIdStr != null) ? UUID.fromString(cityIdStr) : null;

        TravelStatus travelStatusConverted = travelStatus != null ? TravelStatus.valueOf(travelStatus) : null;

        return new TravelCacheDTO(travelId, cityId, customerId, travelStatusConverted, finalLatitude, finalLongitude, polylineRoute, distance, duration);
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

            UUID cityId = customerRepository.findCityIdById(travel.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("City não encontrada."));

            TravelCacheDTO travelCacheDTO = travelCacheMapper(travel, cityId);

            storeTravelStaticCache(travelCacheDTO);

            return travelCacheDTO;
        }
    }

    private TravelCacheDTO travelCacheMapper(Travel travel, UUID cityId) {
        return new TravelCacheDTO(
                travel.getId(),
                cityId,
                travel.getCustomerId(),
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
