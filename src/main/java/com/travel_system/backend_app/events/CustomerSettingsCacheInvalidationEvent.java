package com.travel_system.backend_app.events;

import java.util.UUID;

public record CustomerSettingsCacheInvalidationEvent(
        UUID customerId
) implements CustomerCacheInvalidationEvent {
}
