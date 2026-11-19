package com.travel_system.backend_app.exceptions.standardError;

import com.travel_system.backend_app.model.enums.BusinessErrorCode;

import java.time.LocalDate;
import java.util.Map;

public class BusinessRuleError extends StandardError {

    private BusinessErrorCode errorCode;
    private Map<String, Object> details;

    public BusinessRuleError(LocalDate timeStamp, int status, String error, String path, BusinessErrorCode errorCode, Map<String, Object> details) {
        super(timeStamp, status, error, path);
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
