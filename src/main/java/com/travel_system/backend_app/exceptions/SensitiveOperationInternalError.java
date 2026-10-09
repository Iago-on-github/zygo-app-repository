package com.travel_system.backend_app.exceptions;

public class SensitiveOperationInternalError extends RuntimeException {
    public SensitiveOperationInternalError(String message, Exception e) {
        super(message);
    }
}
