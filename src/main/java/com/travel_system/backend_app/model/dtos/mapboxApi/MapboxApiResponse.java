package com.travel_system.backend_app.model.dtos.mapboxApi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MapboxApiResponse(
        @JsonProperty("routes") List<RoutesDTO> routes,
        @JsonProperty("waypoints") List<WaypointsDTO> waypoints,
        @JsonProperty("code") String code,
        @JsonProperty("uuid") String uuid
) {}
