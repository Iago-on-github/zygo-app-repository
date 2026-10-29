package com.travel_system.backend_app.exceptions;

public class RegistrationRequestNotEditableException extends RuntimeException {
    public RegistrationRequestNotEditableException(String message) {
        super(message);
    }
}
