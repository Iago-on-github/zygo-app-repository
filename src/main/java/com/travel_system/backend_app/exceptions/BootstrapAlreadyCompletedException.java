package com.travel_system.backend_app.exceptions;

public class BootstrapAlreadyCompletedException extends RuntimeException {
    public BootstrapAlreadyCompletedException(String message) {
        super(message);
    }
}
