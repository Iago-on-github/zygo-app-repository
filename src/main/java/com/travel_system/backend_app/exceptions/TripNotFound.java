package com.travel_system.backend_app.exceptions;

public class TripNotFound extends RuntimeException {
    public TripNotFound(String message) {
        super(message);
    }
}
