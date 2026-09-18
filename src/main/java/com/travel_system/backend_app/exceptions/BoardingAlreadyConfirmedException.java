package com.travel_system.backend_app.exceptions;

public class BoardingAlreadyConfirmedException extends RuntimeException {
    public BoardingAlreadyConfirmedException(String message) {
        super(message);
    }
}
