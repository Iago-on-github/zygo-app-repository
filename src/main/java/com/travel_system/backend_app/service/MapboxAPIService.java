package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import com.travel_system.backend_app.interfaces.MapboxAPICalling;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.MapboxApiResponse;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RoutesDTO;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Service
public class MapboxAPIService implements MapboxAPICalling {
    @Value("${mapbox.access.token}")
    private String accessToken;

    private final WebClient webClient;
    private final TravelRepository travelRepository;

    private Logger logger = LoggerFactory.getLogger(MapboxAPIService.class);

    @Autowired
    public MapboxAPIService(WebClient webClient, TravelRepository travelRepository) {
        this.webClient = webClient;
        this.travelRepository = travelRepository;
    }

    // chamada bruta da api
    @Override
    public RouteDetailsDTO calculateRoute(Double originLong, Double originLat, Double destLong, Double destLat) {
        if (originLong == null || originLat == null || destLong == null || destLat == null) {
            logger.debug("[calculateRoute] dados de coordenada inválidos ou insuficientes:{}, {}, {}, {}", originLong, originLat, destLong, destLat);
            return null;
        }

        logger.info("[calculateRoute] dados de coordenadas validados, criando waypoints e fazendo chamada à api...");

        String waypoints = originLong + "," + originLat + ";" + destLong + "," + destLat;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/mapbox/driving/{waypoints}")
                        .queryParam("geometries","polyline")
                        .queryParam("overview","full")
                        .queryParam("access_token", accessToken)
                        .build(waypoints))
                .retrieve()
                .bodyToMono(MapboxApiResponse.class)
                .flatMap(response -> Mono.justOrEmpty(RouteDetailsMapper(response)))
                .block();
    }

    // retorna distância/tempo restante com base na localização atual
    public RouteDetailsDTO recalculateETA(Double currentLng, Double currentLat, Double finalLong, Double finalLat) {
        if (currentLng == null || currentLat == null || finalLong == null || finalLat == null) {
            logger.debug("[recalculateETA] dados de coordenada inválidos ou insuficientes: {}, {}, {}, {}", currentLng, currentLat, finalLong, finalLat);
            return null;
        }

        return calculateRoute(currentLng, currentLat, finalLong, finalLat);
    }

    // salva os dados de distance, duration e polyline na entidade Travel
    @Transactional
    public void getRouteDetailsDTO(Double originLong, Double originLat, Double destLong, Double destLat) {
        if (originLong == null || originLat == null || destLong == null || destLat == null) {
            logger.debug("[getRouteDetailsDTO] dados de coordenada inválidos ou insuficientes:{}, {}, {}, {}", originLong, originLat, destLong, destLat);
            return;
        }

        RouteDetailsDTO staticRouteDetails = calculateRoute(originLong, originLat, destLong, destLat);

        travelRepository.save(travelMapper(staticRouteDetails));
    }

    // padroniza a decodificação do polyline
    private Travel travelMapper(RouteDetailsDTO routeDetailsDTO) {
        Travel travelEntity = new Travel();

        travelEntity.setDistance(routeDetailsDTO.distance());
        travelEntity.setDuration(routeDetailsDTO.duration());
        travelEntity.setPolylineRoute(routeDetailsDTO.geometry());

        return travelEntity;
    }

    private RouteDetailsDTO RouteDetailsMapper(MapboxApiResponse mapboxApiResponse) {
        System.out.println("DEBUGGING ROUTE: " + mapboxApiResponse.code());

        if (mapboxApiResponse.routes().isEmpty()) {
            logger.debug("[RouteDetailsMapper] routes is empty: {}", mapboxApiResponse.routes());
            return null;
        }

        RoutesDTO routesDto = mapboxApiResponse.routes().getFirst();

        return new RouteDetailsDTO(
                (double) Math.round(routesDto.duration()),
                (double) Math.round(routesDto.distance()),
                routesDto.geometry()
        );
    }
}
