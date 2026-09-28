package com.travel_system.backend_app.exceptions;

public class ProfileAlreadyExistsInCustomer extends RuntimeException {
    public ProfileAlreadyExistsInCustomer(String message) {
        super(message);
    }
}
