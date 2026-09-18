package com.travel_system.backend_app.exceptions;

public class StudentAlreadyLinkedToTrip extends RuntimeException {
    public StudentAlreadyLinkedToTrip(String message) {
        super(message);
    }
}
