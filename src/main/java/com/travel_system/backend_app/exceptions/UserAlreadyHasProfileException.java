package com.travel_system.backend_app.exceptions;

public class UserAlreadyHasProfileException extends RuntimeException {
    public UserAlreadyHasProfileException(String message) {
        super(message);
    }
}
