package com.travel_system.backend_app.exceptions;

public class RegistrationStatusNotCompatible extends RuntimeException {
    public RegistrationStatusNotCompatible(String message) {
        super(message);
    }
}
