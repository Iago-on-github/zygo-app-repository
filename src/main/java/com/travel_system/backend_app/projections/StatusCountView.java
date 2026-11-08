package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;

public interface StatusCountView {
    TransportRegistrationStatus getStatus();
    Long getTotal();
}
