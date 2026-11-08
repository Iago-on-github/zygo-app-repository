package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;

import java.util.UUID;

public interface RouteScheduleCountView {
    UUID getStandardRouteId();
    TravelPeriod getTravelPeriod();
    TravelDirection getTravelDirection();
    Long getTotal();

}
