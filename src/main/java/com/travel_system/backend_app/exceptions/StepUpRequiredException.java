package com.travel_system.backend_app.exceptions;

public class StepUpRequiredException extends RuntimeException {
    public StepUpRequiredException(String message) {
        super(message);
    }
}
