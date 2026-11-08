package com.travel_system.backend_app.projections;

import java.util.UUID;

public interface RouteCountView {
    UUID getId();
    String getStandardRouteName();
    long getTotal();
}
