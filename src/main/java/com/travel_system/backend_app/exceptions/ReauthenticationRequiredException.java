package com.travel_system.backend_app.exceptions;

public class ReauthenticationRequiredException extends RuntimeException {
    public ReauthenticationRequiredException(String message) {
        super(message);
    }
}
