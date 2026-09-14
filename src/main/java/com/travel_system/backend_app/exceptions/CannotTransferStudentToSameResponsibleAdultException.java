package com.travel_system.backend_app.exceptions;

public class CannotTransferStudentToSameResponsibleAdultException extends RuntimeException {
    public CannotTransferStudentToSameResponsibleAdultException(String message) {
        super(message);
    }
}
