package com.travel_system.backend_app.exceptions;

public class ScheduledTripAlreadyExistsException extends RuntimeException {
    public ScheduledTripAlreadyExistsException(String message) {
        super(message);
    }
}
