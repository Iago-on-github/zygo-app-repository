package com.travel_system.backend_app.exceptions;

public class HealthDeclarationAlreadyExistsException extends RuntimeException
{
    public HealthDeclarationAlreadyExistsException(String message) {
        super(message);
    }
}
