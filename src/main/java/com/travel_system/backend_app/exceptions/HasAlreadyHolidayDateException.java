package com.travel_system.backend_app.exceptions;

public class HasAlreadyHolidayDateException extends RuntimeException {
    public HasAlreadyHolidayDateException(String message) {
        super(message);
    }
}
