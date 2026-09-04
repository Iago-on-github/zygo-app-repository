package com.travel_system.backend_app.exceptions;

public class EmptyMandatoryFieldsFoundException extends RuntimeException {

    public EmptyMandatoryFieldsFoundException(String message) {
        super(message);
    }
}
