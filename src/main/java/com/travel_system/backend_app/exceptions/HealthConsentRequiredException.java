package com.travel_system.backend_app.exceptions;

public class HealthConsentRequiredException extends RuntimeException {
    public HealthConsentRequiredException(String message) {
        super(message);
    }
}
