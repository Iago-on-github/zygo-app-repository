package com.travel_system.backend_app.events;

import java.util.UUID;

public record ClearRestrictionCacheEvent(UUID userAccountId) {
}
