package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface RegistrationStatusView {
    UUID getOwnerId();
    TransportRegistrationStatus getStatus();
    LocalDate getTermEnd();
    Boolean getHasNextTerm();
    RegistrationRequestType getOpenRequestType();
    RegistrationRequestStatus getOpenRequestStatus();
    Instant getLastRenewalRejectedAt();
}
