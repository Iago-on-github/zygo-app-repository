package com.travel_system.backend_app.exceptions;

public class TripEntryLimitExceededException extends RuntimeException {
    public TripEntryLimitExceededException(String message) {
        super(message);
    }
}
