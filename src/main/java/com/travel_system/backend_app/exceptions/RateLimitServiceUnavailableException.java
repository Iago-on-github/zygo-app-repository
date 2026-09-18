package com.travel_system.backend_app.exceptions;

public class RateLimitServiceUnavailableException extends RuntimeException {
    public RateLimitServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
