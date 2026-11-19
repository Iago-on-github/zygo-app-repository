package com.travel_system.backend_app.model.enums;

/*
* atua como uma rede genércia de execeptions de regras de negócio
* */

import org.springframework.http.HttpStatus;

public enum BusinessErrorCode {

    DRIVER_HAS_SCHEDULED_TRAVEL(HttpStatus.CONFLICT, "Você possui uma viagem agendada.");

    private final HttpStatus httpStatus;
    private final String details;

    BusinessErrorCode(HttpStatus httpStatus, String details) {
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDetails() {
        return details;
    }
}
