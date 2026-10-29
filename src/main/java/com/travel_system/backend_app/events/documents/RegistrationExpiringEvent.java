package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDate;
import java.util.UUID;

public record RegistrationExpiringEvent(
        UUID registrationId,
        UUID customerId,
        OwnerBuilt owner,
        LocalDate termEnd,
        long daysLeft,
        boolean hasDraftRenewal
) {
}
