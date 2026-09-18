package com.travel_system.backend_app.model.dtos.mapboxApi;

public record RouteDeviationDTO(
        double distanceToRouteMeters, // distância mínima até a rota
        boolean isOffRoute, // se a viagem está fora da rota
        double nearestPointLat, // lat do ponto mais proximo da rota
        double nearestPointLng // lng do ponto mais proximo da rota
) {
}
