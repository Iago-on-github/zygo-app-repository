package com.travel_system.backend_app.exceptions;

public class ProfilePictureNotFoundException extends RuntimeException {
    public ProfilePictureNotFoundException(String message) {
        super(message);
    }
}
