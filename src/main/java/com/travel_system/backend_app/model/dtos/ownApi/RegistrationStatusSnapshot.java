package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;

import java.time.LocalDate;

public record RegistrationStatusSnapshot(
        TransportRegistrationStatus status,
        LocalDate termEnd,
        boolean hasNextTerm,
        RegistrationRequestType openRequestType,
        RegistrationRequestStatus openRequestStatus,
        LocalDate lastRenewalRejectedOn
) {

    public static RegistrationStatusSnapshot none() {
        return new RegistrationStatusSnapshot(null, null, false, null, null, null);
    }

    public static RegistrationStatusSnapshot closed() {
        return new RegistrationStatusSnapshot(TransportRegistrationStatus.CLOSED, null, false, null, null, null);
    }
}
