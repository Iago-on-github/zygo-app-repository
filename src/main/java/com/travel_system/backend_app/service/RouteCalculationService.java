package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDeviationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteCalculationService {

    /*
     * PADRÕES DOS TESTES UNITÁRIOS
     * 1. TESTAR TODOS AS SAÍDAS (RESULTADOS) DOS MÉTODOS EM QUESTÃO
     * 2. OS MÉTODOS SUCCESS DEVEM CONTER "WithSuccess"
     * 3. OS MÉTODOS FAILURE DEVEM CONTER "ThrowException"
     * 4. MÉTODOS COM VÁRIAS POSSÍVEIS SAÍDAS DEVEM SER OBRIGATORIAMENTE ENGLOBADAS EM CLASSES PRÓPRIAS DE SUCCESS E FAILURE (MESMO DENTRO DA SUA CLASSE DE ORIGEM)
     * 5. NUNCA USAR RUNTIME EX. COMO EXCEÇÃO CORINGA, USE A PRÓPRIA EXCEÇÃO LANÇADA NO MÉTODO
     * 6. SEMPRE ADICIONAR UMA BREVE DESCRIÇÃO COM A ANNOTATION '@DisplayName("...")'.
     * 7. OS TESTES DEVEM OBRIGATORIAMENTE SEGUIR O PADRÃO AAA (ARRANGE, ACT & ASSERT) de forma com que todos os cenários sejam cobertos
     *
     */

    private PolylineService polylineService;

    private final double TOLERANCE_DISTANCE = 50.0;
    private final double EARTH_RADIUS_METERS = 6371000;

    private Logger logger = LoggerFactory.getLogger(RouteCalculationService.class);

    public RouteCalculationService(PolylineService polylineService) {
        this.polylineService = polylineService;
    }

    // verifica se a rota foi desviada da rota padrão - tolerância de 50 metros
    public RouteDeviationDTO isRouteDeviation(Double currentLat, Double currentLong, String polylineRoute) {

        if (currentLat == null || currentLong == null || polylineRoute == null) {
            logger.debug("[isRouteDeviation] dados de lat/lng/polyline null, retornando...");
            return new RouteDeviationDTO(0.0, true, 0.0, 0.0);
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
