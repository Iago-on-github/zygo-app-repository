package com.travel_system.backend_app.exceptions;

public class InvalidBootstrapSecretException extends RuntimeException {
    public InvalidBootstrapSecretException(String message) {
        super(message);
    }
}
