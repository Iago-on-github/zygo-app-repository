package com.travel_system.backend_app.exceptions;

public class ResponsibleAdultStudentLimitExceededException extends RuntimeException {
    public ResponsibleAdultStudentLimitExceededException(String message) {
        super(message);
    }
}
