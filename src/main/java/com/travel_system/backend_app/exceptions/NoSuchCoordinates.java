package com.travel_system.backend_app.exceptions;

public class NoSuchCoordinates extends RuntimeException {
    public NoSuchCoordinates(String message) {
        super(message);
    }
}
