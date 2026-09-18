package com.travel_system.backend_app.exceptions;

public class TravelPeriodRequiredException extends RuntimeException {
    public TravelPeriodRequiredException(String message) {
        super(message);
    }
}
