package com.travel_system.backend_app.exceptions;

public class ImageProcessingException extends RuntimeException {
    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImageProcessingException(String message) {

    }
}
