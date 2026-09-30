package com.travel_system.backend_app.exceptions;

public class ErrorWithEmailProcessVerificationException extends RuntimeException {
    public ErrorWithEmailProcessVerificationException(String message) {
        super(message);
    }
}
