package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.TravelStatus;

import java.util.UUID;

public record TravelCacheDTO(UUID travelId,
                             TravelStatus travelStatus,
                             Double finalLatitude,
                             Double finalLongitude,
                             String polylineRoute,
                             Double distance,
                             Double duration) {
}
