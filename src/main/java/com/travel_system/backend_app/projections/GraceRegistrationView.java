package com.travel_system.backend_app.projections;

import java.time.LocalDate;
import java.util.UUID;

public interface GraceRegistrationView {
    UUID getRegistrationId();
    UUID getStudentId();
    UUID getDriverId();
    String getRegistrationNumber();
    LocalDate getTermEnd();
}
