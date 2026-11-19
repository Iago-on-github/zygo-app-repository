package com.travel_system.backend_app.projections;

import java.util.UUID;

public interface ProfileRefView {
    UUID getOwnerId();
    UUID getCustomerId();
}
