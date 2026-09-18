package com.travel_system.backend_app.exceptions;

public class StudentNotLinkedToTripException extends RuntimeException {
    public StudentNotLinkedToTripException(String message) {
        super(message);
    }
}
