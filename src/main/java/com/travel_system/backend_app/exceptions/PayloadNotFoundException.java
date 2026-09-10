package com.travel_system.backend_app.exceptions;

public class PayloadNotFoundException extends RuntimeException {
    public PayloadNotFoundException(String message) {
        super(message);
    }
}
