package com.travel_system.backend_app.exceptions;

public class DuplicatePendingInvitationException extends RuntimeException {
    public DuplicatePendingInvitationException(String message) {
        super(message);
    }
}
