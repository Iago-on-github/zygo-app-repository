package com.travel_system.backend_app.projections;

import java.util.UUID;

public interface RequestDocumentCountView {
    UUID getRequestId();
    Long getTotal();
}
