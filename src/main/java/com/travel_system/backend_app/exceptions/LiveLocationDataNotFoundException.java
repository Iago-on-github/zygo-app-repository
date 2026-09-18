package com.travel_system.backend_app.exceptions;

public class LiveLocationDataNotFoundException extends RuntimeException {
    public LiveLocationDataNotFoundException(String message) {
        super(message);
    }
}
