package com.travel_system.backend_app.exceptions;

public class InvalidTravelDirectionException extends RuntimeException {
    public InvalidTravelDirectionException(String message) {
        super(message);
    }
}
