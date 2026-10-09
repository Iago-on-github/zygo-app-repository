package com.travel_system.backend_app.exceptions;

public class InstitutionNotFoundException extends RuntimeException {
    public InstitutionNotFoundException(String message) {
        super(message);
    }
}
