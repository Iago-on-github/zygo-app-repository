package com.travel_system.backend_app.exceptions;

public class EntityLimitExceededException extends RuntimeException {
    public EntityLimitExceededException(String message) {
        super(message);
    }
}
