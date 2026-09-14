package com.travel_system.backend_app.exceptions;

public class StudentNotAssociatedWithResponsibleAdultException extends RuntimeException {
    public StudentNotAssociatedWithResponsibleAdultException(String message) {
        super(message);
    }
}
