package com.travel_system.backend_app.exceptions;

public class InactiveDriverException extends RuntimeException {
    public InactiveDriverException(String message) {
        super(message);
    }
}
