package com.travel_system.backend_app.exceptions.catchException;

import com.google.api.Http;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.exceptions.standardError.StandardError;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDate;

@ControllerAdvice
public class CapturedAndCustomizedExceptions {

    @ExceptionHandler(InvalidJwtAuthenticationToken.class)
    public final ResponseEntity<StandardError> invalidJwtAuthenticationException(InvalidJwtAuthenticationToken ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EmptyMandatoryFieldsFound.class)
    public final ResponseEntity<StandardError> emptyMandatoryFieldsException(EmptyMandatoryFieldsFound ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoSuchCoordinates.class)
    public final ResponseEntity<StandardError> noSuchCoordinatesException(NoSuchCoordinates ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TripNotFound.class)
    public final ResponseEntity<StandardError> tripNotFoundException(TripNotFound ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TravelException.class)
    public final ResponseEntity<StandardError> travelException(TravelException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RecalculateEtaException.class)
    public final ResponseEntity<StandardError> recalculateEtaException(RecalculateEtaException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(LiveLocationDataNotFoundException.class)
    public final ResponseEntity<StandardError> LiveLocationDataNotFoundException(LiveLocationDataNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TravelStudentAssociationNotFoundException.class)
    public final ResponseEntity<StandardError> TravelStudentAssociationNotFoundException(TravelStudentAssociationNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BoardingAlreadyConfirmedException.class)
    public final ResponseEntity<StandardError> BoardingAlreadyConfirmedException(BoardingAlreadyConfirmedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InactiveDriverException.class)
    public final ResponseEntity<StandardError> InactiveDriverException(InactiveDriverException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StudentAlreadyLinkedToTrip.class)
    public final ResponseEntity<StandardError> StudentAlreadyLinkedToTrip(StudentAlreadyLinkedToTrip ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);

    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public final ResponseEntity<StandardError> MethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public final ResponseEntity<StandardError> EntityNotFoundException (EntityNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<StandardError> buildErrorCustomerResponse(Exception ex, WebRequest webRequest, HttpStatus httpStatus) {
        StandardError standardError = new StandardError(
                LocalDate.now(),
                httpStatus.value(),
                ex.getMessage(),
                webRequest.getDescription(false)
        );

        return ResponseEntity.status(httpStatus).body(standardError);
    }

}
