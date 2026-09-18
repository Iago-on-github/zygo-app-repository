package com.travel_system.backend_app.exceptions;

public class TravelDirectionRequiredException extends RuntimeException {
    public TravelDirectionRequiredException(String message) {
        super(message);
    }
}
