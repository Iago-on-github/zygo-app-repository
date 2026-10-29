package com.travel_system.backend_app.exceptions;

public class RegistrationNotUnderReviewException extends RuntimeException {
    public RegistrationNotUnderReviewException(String message) {
        super(message);
    }
}
