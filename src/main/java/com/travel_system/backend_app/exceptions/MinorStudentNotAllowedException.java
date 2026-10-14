package com.travel_system.backend_app.exceptions;

public class MinorStudentNotAllowedException extends RuntimeException {
    public MinorStudentNotAllowedException(String message) {
        super(message);
    }
}
