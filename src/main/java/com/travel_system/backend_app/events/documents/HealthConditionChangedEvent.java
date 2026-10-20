package com.travel_system.backend_app.events.documents;

import java.util.UUID;

public record HealthConditionChangedEvent(
        UUID healthDeclarationId,
        UUID ownerId,
        boolean hasConditions,
        UUID declaredBy,
        UUID customerId
) {
}
