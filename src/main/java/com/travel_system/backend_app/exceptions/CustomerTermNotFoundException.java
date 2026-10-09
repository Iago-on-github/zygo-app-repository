package com.travel_system.backend_app.exceptions;

public class CustomerTermNotFoundException extends RuntimeException {
    public CustomerTermNotFoundException(String message) {
        super(message);
    }
}
