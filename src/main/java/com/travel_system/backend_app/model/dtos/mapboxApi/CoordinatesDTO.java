package com.travel_system.backend_app.model.dtos.mapboxApi;

import com.mapbox.geojson.Point;

// CONVERTE "POINT" PARA LATITUDE E LONGITUDE

public record CoordinatesDTO(double latitude, double longitude) {
    public static CoordinatesDTO from(Point point) {
        return new CoordinatesDTO(point.latitude(), point.longitude());
    }
}
