package com.travel_system.backend_app.exceptions;

public class InvitationNotPendingException extends RuntimeException {
    public InvitationNotPendingException(String message) {
        super(message);
    }
}
