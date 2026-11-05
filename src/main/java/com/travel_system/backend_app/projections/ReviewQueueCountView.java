package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.time.Instant;

public interface ReviewQueueCountView {
    RegistrationRequestType getType();
    Long getTotal();
    Instant getOldestSubmittedAt();
}
