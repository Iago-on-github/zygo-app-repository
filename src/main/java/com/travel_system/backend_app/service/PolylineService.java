package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.utils.PolylineUtils;
import com.travel_system.backend_app.exceptions.NoSuchCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolylineService {

    private Logger logger = LoggerFactory.getLogger(PolylineService.class);

    public List<Point> formattedPolylineDecoded(String polylineRoute) {
        int precision = 5;

        if (polylineRoute.isEmpty()) {
            logger.debug("[formattedPolylineDecoded] Polyline recebido está vazio: {} ", polylineRoute);
            return null;
        }

        return PolylineUtils.decode(polylineRoute, precision);
    }

    public String formattedPolylineEncoded(List<Point> polylineRoute) {
        int precision = 5;

        if (polylineRoute.isEmpty()) {
            logger.debug("[formattedPolylineEncoded] Polyline recebido está vazio: {} ", polylineRoute);
            return null;
        }

        return PolylineUtils.encode(polylineRoute, precision);
    }
}
