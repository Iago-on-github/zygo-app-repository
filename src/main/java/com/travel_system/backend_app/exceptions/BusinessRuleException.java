package com.travel_system.backend_app.exceptions;

import com.travel_system.backend_app.model.enums.BusinessErrorCode;

import java.util.Map;

public class BusinessRuleException extends RuntimeException {
    private final BusinessErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessRuleException(BusinessErrorCode errorCode, Map<String, Object> details) {
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
