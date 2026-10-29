package com.travel_system.backend_app.exceptions;

import com.travel_system.backend_app.model.enums.UpdateBlockReason;

public class RegistrationNotUpdatableException extends RuntimeException {
    private final UpdateBlockReason reason;

    public RegistrationNotUpdatableException(UpdateBlockReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public UpdateBlockReason getReason() {
        return reason;
    }
}
