package com.travel_system.backend_app.model.enums;

public enum EffectiveRegistrationStatus {
    NONE(false),
    PENDING(false),
    ACTIVE(true),
    GRACE(true),
    EXPIRED(false),
    CLOSED(false);

    private final boolean allowsAccess;

    EffectiveRegistrationStatus(boolean allowsAccess) {
        this.allowsAccess = allowsAccess;
    }

    public boolean allowsAccess() {
        return allowsAccess;
    }
}
