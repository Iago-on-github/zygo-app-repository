package com.travel_system.backend_app.events;

// faz a limpeza do cache com cache evict via evento (after commit)

import java.util.UUID;

public record CustomerTimeZoneChangedEvent(UUID customerIdKey) {
}
