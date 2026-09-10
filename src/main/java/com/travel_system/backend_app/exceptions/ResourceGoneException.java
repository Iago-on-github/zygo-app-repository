package com.travel_system.backend_app.exceptions;

public class ResourceGoneException extends RuntimeException {
    public ResourceGoneException(String message) {
        super(message);
    }
}
