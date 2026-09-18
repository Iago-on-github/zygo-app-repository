package com.travel_system.backend_app.exceptions;

public class CustomerMismatchException extends RuntimeException {
    public CustomerMismatchException(String message) {
        super(message);
    }
}
