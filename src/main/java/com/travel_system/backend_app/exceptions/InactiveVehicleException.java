package com.travel_system.backend_app.exceptions;

public class InactiveVehicleException extends RuntimeException {
    public InactiveVehicleException(String message) {
        super(message);
    }
}
