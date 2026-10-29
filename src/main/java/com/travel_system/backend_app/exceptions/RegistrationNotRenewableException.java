package com.travel_system.backend_app.exceptions;

import com.travel_system.backend_app.model.enums.RenewalBlockReason;

public class RegistrationNotRenewableException extends RuntimeException {
    private final RenewalBlockReason reason;

    public RegistrationNotRenewableException(RenewalBlockReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RenewalBlockReason getReason() {
        return reason;
    }
}
