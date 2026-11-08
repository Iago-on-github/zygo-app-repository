package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;

import java.time.Instant;
import java.util.UUID;

public interface StalledRegistrationView {
    UUID getRegistrationId();
    UUID getStudentId();
    UUID getDriverId();
    UUID getRequestId();
    RegistrationRequestStatus getRequestStatus();
    Instant getSince();
}
