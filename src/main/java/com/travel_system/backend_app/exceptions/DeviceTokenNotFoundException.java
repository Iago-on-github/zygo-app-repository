package com.travel_system.backend_app.exceptions;

public class DeviceTokenNotFoundException extends RuntimeException {
    public DeviceTokenNotFoundException(String message) {
        super(message);
    }
}
