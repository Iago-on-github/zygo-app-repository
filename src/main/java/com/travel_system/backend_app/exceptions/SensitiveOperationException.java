package com.travel_system.backend_app.exceptions;

public class SensitiveOperationException extends RuntimeException {
    public SensitiveOperationException(String message) {
        super(message);
    }
}
