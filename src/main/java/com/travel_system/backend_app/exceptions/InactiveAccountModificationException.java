package com.travel_system.backend_app.exceptions;

public class InactiveAccountModificationException extends RuntimeException {
    public InactiveAccountModificationException(String message) {
        super(message);
    }
}
