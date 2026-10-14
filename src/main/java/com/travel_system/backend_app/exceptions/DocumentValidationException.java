package com.travel_system.backend_app.exceptions;

public class DocumentValidationException extends RuntimeException {
    public DocumentValidationException(String message) {
        super(message);
    }
}
