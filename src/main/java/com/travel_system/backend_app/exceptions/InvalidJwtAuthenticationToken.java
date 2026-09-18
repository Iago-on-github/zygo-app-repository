package com.travel_system.backend_app.exceptions;

import javax.naming.AuthenticationException;

public class InvalidJwtAuthenticationToken extends RuntimeException {
    public InvalidJwtAuthenticationToken(String message) {
        super(message);
    }
}
