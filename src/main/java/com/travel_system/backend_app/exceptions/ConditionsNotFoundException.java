package com.travel_system.backend_app.exceptions;

public class ConditionsNotFoundException extends RuntimeException {
    public ConditionsNotFoundException(String message) {
        super(message);
    }
}
