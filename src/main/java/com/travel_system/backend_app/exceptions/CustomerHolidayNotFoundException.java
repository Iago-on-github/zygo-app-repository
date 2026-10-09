package com.travel_system.backend_app.exceptions;

public class CustomerHolidayNotFoundException extends RuntimeException {
    public CustomerHolidayNotFoundException(String message) {
        super(message);
    }
}
