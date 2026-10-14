package com.travel_system.backend_app.exceptions;

public class DocumentConflictException extends RuntimeException {
    public DocumentConflictException(String message) {
        super(message);
    }

  public DocumentConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
