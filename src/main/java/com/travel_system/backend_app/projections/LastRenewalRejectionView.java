package com.travel_system.backend_app.projections;

import java.time.Instant;
import java.util.UUID;

public interface LastRenewalRejectionView {

    UUID getRegistrationId();
    Instant getRejectedAt();
}
