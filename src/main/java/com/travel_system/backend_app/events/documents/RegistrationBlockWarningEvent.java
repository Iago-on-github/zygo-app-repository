package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.time.LocalDate;
import java.util.UUID;

public record RegistrationBlockWarningEvent(
        UUID registrationId,
        UUID customerId,
        OwnerBuilt ownerBuilt,
        String registrationNumber,
        LocalDate graceEnd,
        boolean notifyOwner,
        boolean notifyAdmin
) {
}
