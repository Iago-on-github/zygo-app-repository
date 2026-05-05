package com.travel_system.backend_app.exceptions;

public class InvalidLiveLocationDataException extends RuntimeException {
    public InvalidLiveLocationDataException(String message) {
        super(message);
    }
}
