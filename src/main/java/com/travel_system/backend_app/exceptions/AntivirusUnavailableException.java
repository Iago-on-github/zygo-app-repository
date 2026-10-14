package com.travel_system.backend_app.exceptions;

public class AntivirusUnavailableException extends RuntimeException {
    public AntivirusUnavailableException(String message) {
        super(message);
    }

    public AntivirusUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
