package com.travel_system.backend_app.events.documents;

import java.util.List;

public record DocumentFilesDeletionRequestedEvent(List<String> storageKeys) {
}
