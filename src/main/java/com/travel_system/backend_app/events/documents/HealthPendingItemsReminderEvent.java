package com.travel_system.backend_app.events.documents;

import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;

import java.util.UUID;

public record HealthPendingItemsReminderEvent(
        OwnerBuilt owner,
        int pendingCount,
        UUID customerId
) {
}
