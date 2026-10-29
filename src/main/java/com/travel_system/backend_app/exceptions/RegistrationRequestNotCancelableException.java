package com.travel_system.backend_app.exceptions;

public class RegistrationRequestNotCancelableException extends RuntimeException {
    public RegistrationRequestNotCancelableException(String message) {
        super(message);
    }
}
