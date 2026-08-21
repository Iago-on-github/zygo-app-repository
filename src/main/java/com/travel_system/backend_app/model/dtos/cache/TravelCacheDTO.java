package com.travel_system.backend_app.model.dtos.cache;

import com.travel_system.backend_app.model.enums.TravelStatus;

import java.util.UUID;

public record TravelCacheDTO(UUID travelId,
                             UUID cityId,
                             UUID customerId,
                             TravelStatus travelStatus,
                             Double finalLatitude,
                             Double finalLongitude,
                             String polylineRoute,
                             Double distance,
                             Double duration) {
}
