package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.util.UUID;

// evento para notificar os admins do Customer
public record RegistrationSubmittedEvent(
        UUID registrationRequestId,
        UUID registrationId,
        UUID customerId,
        OwnerBuilt owner,
        RegistrationRequestType requestType
) {
}
