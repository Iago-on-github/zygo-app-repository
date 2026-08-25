package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import com.travel_system.backend_app.interfaces.MapboxAPICalling;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.MapboxApiResponse;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RoutesDTO;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.transaction.Transactional;
import org.locationtech.jts.util.CollectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
    public RouteDetailsDTO calculateRoute(Double originLong, Double originLat, Double destLong, Double destLat, List<Point> waypoint) {
        if (originLong == null || originLat == null || destLong == null || destLat == null) {
            logger.debug("[calculateRoute] dados de coordenada inválidos ou insuficientes:{}, {}, {}, {}", originLong, originLat, destLong, destLat);
            return null;
        }

        logger.info("[calculateRoute] dados de coordenadas validados, criando waypoints e fazendo chamada à api...");

        // verifica se é null/empty
        if (CollectionUtils.isEmpty(waypoint)) {
            waypoint = List.of();
        }

        String originCoords = originLong + "," + originLat;
        String destinationCoords = destLong + "," + destLat;

        List<String> waypointCoordinates = waypoint.stream()
                .map(point -> point.longitude() + "," + point.latitude()).toList();

        // combina ambos os fluxos de coordenadas (waypoints + originCoords/destinationCoords) mantendo a ordem dos elementos
        String waypoints = Stream.concat(
                Stream.of(originCoords),
                Stream.concat(
                        waypointCoordinates.stream(),
                        Stream.of(destinationCoords)
                )
        ).collect(Collectors.joining(";"));

        logger.info("[calculateRoute] calculando rota com {} pontos intermediários", waypointCoordinates.size());

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

        return calculateRoute(currentLng, currentLat, finalLong, finalLat, null);
    }

    // retorna dados de preview para a viagem
    public TravelPreviewDTO getTripPreview(Double originLong, Double originLat, Double destLong, Double destLat) {
        if (originLong == null || originLat == null || destLong == null || destLat == null) {
            logger.debug("[getTripPreview] dados de coordenada inválidos ou insuficientes: {}, {}, {}, {}", originLong, originLat, destLong, destLat);
            return null;
        }

        logger.info("[getTripPreview] dados de coordenadas validados, criando waypoints e fazendo chamada à api...");

        String waypoints = originLong + "," + originLat + ";" + destLong + "," + destLat;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/mapbox/driving/{waypoints}")
                        .queryParam("geometries", "polyline")
                        .queryParam("overview", "full")
                        .queryParam("access_token", accessToken)
                        .build(waypoints))
                .retrieve()
                .bodyToMono(MapboxApiResponse.class)
                .flatMap(response -> Mono.justOrEmpty(travelPreviewMapper(response)))
                .block();
    }

    // salva os dados de distance, duration e polyline na entidade Travel
    @Transactional
    public void getAndSaveRouteDetailsDTO(Double originLong, Double originLat, Double destLong, Double destLat) {
        if (originLong == null || originLat == null || destLong == null || destLat == null) {
            logger.debug("[getRouteDetailsDTO] dados de coordenada inválidos ou insuficientes:{}, {}, {}, {}", originLong, originLat, destLong, destLat);
            return;
        }

        RouteDetailsDTO staticRouteDetails = calculateRoute(originLong, originLat, destLong, destLat, null);

        travelRepository.save(travelMapper(staticRouteDetails));
    }

    public RouteDetailsDTO calculateStandardRoute(Double originLong, Double originLat, Double destinationLong,  Double destinationLat, List<Point> waypoint) {
        if (originLong == null || originLat == null || destinationLong == null || destinationLat == null || waypoint.isEmpty()) {
            logger.debug("[calculateStandardRoute] dados de coordenada inválidos ou insuficientes: {}, {}, {}, {}, {} ", originLong, originLat, destinationLong, destinationLat, waypoint);
            return null;
        }

        return calculateRoute(originLong, originLat, destinationLong, destinationLat, waypoint);
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

    private TravelPreviewDTO travelPreviewMapper(MapboxApiResponse mapboxApiResponse) {
        System.out.println("[travelPreviewMapper] - DEBUGGING ROUTE: " + mapboxApiResponse.code());

        if (mapboxApiResponse.routes().isEmpty()) {
            logger.debug("[travelPreviewMapper] routes is empty: {}", mapboxApiResponse.routes());
            return null;
        }

        RoutesDTO routesDto = mapboxApiResponse.routes().getFirst();

        // retorna null nos campos "destinationCity" e "arrivalTime", são preenchidos na chamada
        return new TravelPreviewDTO(
                (double) Math.round(routesDto.distance()),
                (double) Math.round(routesDto.duration()),
                null,
                null
        );
    }

}
