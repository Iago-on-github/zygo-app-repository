package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RouteCalculationService {
    private final PolylineService polylineService;
    private final TravelRepository travelRepository;

    private final double TOLERANCE_DISTANCE = 50.0;

    private final double EARTH_RADIUS_METERS = 6371000;

    private Logger logger = LoggerFactory.getLogger(RouteCalculationService.class);

    public RouteCalculationService(PolylineService polylineService, TravelRepository travelRepository) {
        this.polylineService = polylineService;
        this.travelRepository = travelRepository;
    }

    // verifica se a rota foi desviada da rota padrão - tolerância de 50 metros
    public RouteDeviationDTO isRouteDeviation(RouteDeviationRequestDTO routeDeviationRequestDTO) {
        UUID travelId = routeDeviationRequestDTO.travelId();
        Double currentLat = routeDeviationRequestDTO.currentLat();
        Double currentLong = routeDeviationRequestDTO.currentLong();

        if (travelId == null || currentLat == null || currentLong == null) {
            logger.debug("[isRouteDeviation] travelId/currentLat/currentLong null");
            return new RouteDeviationDTO(0.0, false, 0.0, 0.0);
        }

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new EntityNotFoundException("Viagem " + travelId + " não encontrada"));

        String polylineRoute = travel.getPolylineRoute();

        if (polylineRoute == null) {
            logger.debug("[isRouteDeviation] polyline null, retornando...");
            return new RouteDeviationDTO(0.0, false, 0.0, 0.0);
        }

        double minDistance = Double.MAX_VALUE;
        double projLng = 0;
        double projLat = 0;

        List<Point> decodePolyline = polylineService.formattedPolylineDecoded(polylineRoute);
        Point driverCurrentLoc = Point.fromLngLat(currentLong, currentLat);

        if (decodePolyline.size() < 2) {
            logger.debug("[isRouteDeviation] decoded polyline tem menos que duas partes, retornando...");
            return new RouteDeviationDTO(0, false, currentLat, currentLong);
        }

        for (int i = 0; i < decodePolyline.size() - 1; i++) {
            Point P_A = decodePolyline.get(i);
            Point P_B = decodePolyline.get(i + 1);

            double[] routeDirection = {P_B.longitude() - P_A.longitude(), P_B.latitude() - P_A.latitude()};
            double[] routePoint = {driverCurrentLoc.longitude() - P_A.longitude(), driverCurrentLoc.latitude() - P_A.latitude()};

            double dotWV = routePoint[0] * routeDirection[0] + routePoint[1] * routeDirection[1];
            double dotVV = routeDirection[0] * routeDirection[0] + routeDirection[1] * routeDirection[1];

            double t = dotWV / dotVV;
            if (t < 0) t = 0;
            if (t > 1) t = 1;

            double projLngCandidate = P_A.longitude() + t * routeDirection[0];
            double projLatCandidate = P_A.latitude() + t * routeDirection[1];

            double distance = calculateHaversineDistanceInMeters(
                    driverCurrentLoc.latitude(),
                    driverCurrentLoc.longitude(),
                    projLatCandidate,
                    projLngCandidate);

            if (distance < minDistance) {
                minDistance = distance;
                projLng = projLngCandidate;
                projLat = projLatCandidate;
            }
        }

        boolean isOffRoute = minDistance > TOLERANCE_DISTANCE; // tolerância de distancia

        return new RouteDeviationDTO(
                minDistance,
                isOffRoute,
                projLat,
                projLng
        );
    }

    protected Double calculateHaversineDistanceInMeters(double currentLat, double currentLng, double projLat, double projLng) {
        // convete todas as coord. para radius
        double lat1Rad = Math.toRadians(currentLat);
        double lat2Rad = Math.toRadians(projLat);

        // calc da diferença entre as coordenadas em radius

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLng = Math.toRadians(projLng - currentLng);

        // aplica a fórmula de Haversine
        double haversineFormule = Math.pow(Math.sin(deltaLat / 2), 2) + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(deltaLng / 2), 2);

        // aplica o arco-cosseno
        // "arccos" é o angulo central subtendido pelo arco (distancia)
        double arccos = 2 * Math.atan2(Math.sqrt(haversineFormule), Math.sqrt(1 - haversineFormule));

        // distancia final
        return EARTH_RADIUS_METERS * arccos;
    }

}
