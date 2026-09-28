package com.travel_system.backend_app.exceptions;

public class UserNotInvitableException extends RuntimeException {
    public UserNotInvitableException(String message) {
        super(message);
    }
}
