package com.travel_system.backend_app.exceptions.catchException;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.exceptions.standardError.StandardError;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
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

    @ExceptionHandler(EmailNotVerifiedException.class)
    public final ResponseEntity<StandardError> EmailNotVerifiedException(EmailNotVerifiedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NotAuthorizedException.class)
    public final ResponseEntity<StandardError> NotAuthorizedException(NotAuthorizedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(CustomerMismatchException.class)
    public final ResponseEntity<StandardError> CustomerMismatchException(CustomerMismatchException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public final ResponseEntity<StandardError> ReauthenticationRequiredException(ReauthenticationRequiredException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EmptyMandatoryFieldsFoundException.class)
    public final ResponseEntity<StandardError> emptyMandatoryFieldsException(EmptyMandatoryFieldsFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InactiveVehicleException.class)
    public final ResponseEntity<StandardError> InactiveVehicleException(InactiveVehicleException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoSuchCoordinates.class)
    public final ResponseEntity<StandardError> noSuchCoordinatesException(NoSuchCoordinates ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DomainValidationException.class)
    public final ResponseEntity<StandardError> DomainValidationException(DomainValidationException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TripEntryLimitExceededException.class)
    public final ResponseEntity<StandardError> TripEntryLimitExceededException(TripEntryLimitExceededException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ErrorWithEmailProcessVerificationException.class)
    public final ResponseEntity<StandardError> ErrorWithEmailProcessVerificationException(ErrorWithEmailProcessVerificationException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public final ResponseEntity<StandardError> tripNotFoundException(TripNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CnhNotFoundException.class)
    public final ResponseEntity<StandardError> CnhNotFoundException(CnhNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InstitutionNotFoundException.class)
    public final ResponseEntity<StandardError> InstitutionNotFoundException(InstitutionNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public final ResponseEntity<StandardError> VehicleNotFoundException(VehicleNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TravelException.class)
    public final ResponseEntity<StandardError> travelException(TravelException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HasAlreadyHolidayDateException.class)
    public final ResponseEntity<StandardError> HasAlreadyHolidayDateException(HasAlreadyHolidayDateException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CourseAlreadyExistsException.class)
    public final ResponseEntity<StandardError> CourseAlreadyExistsException(CourseAlreadyExistsException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResourceInUseException.class)
    public final ResponseEntity<StandardError> ResourceInUseException(ResourceInUseException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ScheduledTripAlreadyExistsException.class)
    public final ResponseEntity<StandardError> ScheduledTripAlreadyExistsException(ScheduledTripAlreadyExistsException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    public final ResponseEntity<StandardError> EmailAlreadyVerifiedException(EmailAlreadyVerifiedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(StudentAlreadyHasResponsibleAdultException.class)
    public final ResponseEntity<StandardError> StudentAlreadyHasResponsibleAdultException(StudentAlreadyHasResponsibleAdultException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvitationExpiredException.class)
    public final ResponseEntity<StandardError> InvitationExpiredException(InvitationExpiredException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UserAlreadyHasProfileException.class)
    public final ResponseEntity<StandardError> UserAlreadyHasProfileException(UserAlreadyHasProfileException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvitationRoleMismatchException.class)
    public final ResponseEntity<StandardError> InvitationRoleMismatchException(InvitationRoleMismatchException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MinorStudentResponsibleAdultTransferRequiredException.class)
    public final ResponseEntity<StandardError> MinorStudentResponsibleAdultTransferRequiredException(MinorStudentResponsibleAdultTransferRequiredException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResponsibleAdultStudentLimitExceededException.class)
    public final ResponseEntity<StandardError> ResponsibleAdultStudentLimitExceededException(ResponsibleAdultStudentLimitExceededException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BootstrapAlreadyCompletedException.class)
    public final ResponseEntity<StandardError> BootstrapAlreadyCompletedException(BootstrapAlreadyCompletedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(FileTooLargeException.class)
    public final ResponseEntity<StandardError> FileTooLargeException(FileTooLargeException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(RecalculateEtaException.class)
    public final ResponseEntity<StandardError> recalculateEtaException(RecalculateEtaException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(LiveLocationDataNotFoundException.class)
    public final ResponseEntity<StandardError> LiveLocationDataNotFoundException(LiveLocationDataNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CustomerHolidayNotFoundException.class)
    public final ResponseEntity<StandardError> CustomerHolidayNotFoundException(CustomerHolidayNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CourseNotFoundException.class)
    public final ResponseEntity<StandardError> CourseNotFoundException(CourseNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PayloadNotFoundException.class)
    public final ResponseEntity<StandardError> PayloadNotFoundException(PayloadNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResponsibleAdultHasNoStudentsException.class)
    public final ResponseEntity<StandardError> ResponsibleAdultHasNoStudentsException(ResponsibleAdultHasNoStudentsException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TravelStudentAssociationNotFoundException.class)
    public final ResponseEntity<StandardError> TravelStudentAssociationNotFoundException(TravelStudentAssociationNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public final ResponseEntity<StandardError> InvitationNotFoundException(InvitationNotFoundException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BoardingAlreadyConfirmedException.class)
    public final ResponseEntity<StandardError> BoardingAlreadyConfirmedException(BoardingAlreadyConfirmedException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SensitiveOperationException.class)
    public final ResponseEntity<StandardError> SensitiveOperationException(SensitiveOperationException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvitationNotPendingException.class)
    public final ResponseEntity<StandardError> InvitationNotPendingException(InvitationNotPendingException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TravelDirectionRequiredException.class)
    public final ResponseEntity<StandardError> TravelDirectionRequiredException(TravelDirectionRequiredException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidInvitationProcessException.class)
    public final ResponseEntity<StandardError> InvalidInvitationProcessException(InvalidInvitationProcessException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TravelPeriodRequiredException.class)
    public final ResponseEntity<StandardError> TravelPeriodRequiredException(TravelPeriodRequiredException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InactiveDriverException.class)
    public final ResponseEntity<StandardError> InactiveDriverException(InactiveDriverException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StudentAlreadyLinkedToTrip.class)
    public final ResponseEntity<StandardError> StudentAlreadyLinkedToTrip(StudentAlreadyLinkedToTrip ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(HasAlreadyTermDataExistsException.class)
    public final ResponseEntity<StandardError> HasAlreadyTermDataExistsException(HasAlreadyTermDataExistsException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }


    @ExceptionHandler(ConstraintViolationException.class)
    public final ResponseEntity<StandardError> ConstraintViolationException(ConstraintViolationException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(UserNotInvitableException.class)
    public final ResponseEntity<StandardError> UserNotInvitableException(UserNotInvitableException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(InvalidTravelDirectionException.class)
    public final ResponseEntity<StandardError> InvalidTravelDirectionException(InvalidTravelDirectionException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DuplicatePendingInvitationException.class)
    public final ResponseEntity<StandardError> DuplicatePendingInvitation(DuplicatePendingInvitationException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ProfileAlreadyExistsInCustomer.class)
    public final ResponseEntity<StandardError> ProfileAlreadyExistsInCustomer(ProfileAlreadyExistsInCustomer ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InactiveAccountException.class)
    public final ResponseEntity<StandardError> InactiveAccountException(InactiveAccountException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(StandardRouteException.class)
    public final ResponseEntity<StandardError> StandardRouteException(StandardRouteException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EntityLimitExceededException.class)
    public final ResponseEntity<StandardError> EntityLimitExceededException(EntityLimitExceededException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ResourceGoneException.class)
    public final ResponseEntity<StandardError> ResourceGoneException(ResourceGoneException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.GONE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public final ResponseEntity<StandardError> MethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public final ResponseEntity<StandardError> EntityNotFoundException (EntityNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public final ResponseEntity<StandardError> CustomerNotFoundException (CustomerNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CustomerTermNotFoundException.class)
    public final ResponseEntity<StandardError> CustomerTermNotFoundException (CustomerTermNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CityNotFoundException.class)
    public final ResponseEntity<StandardError> CityNotFoundException (CityNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProfilePictureNotFoundException.class)
    public final ResponseEntity<StandardError> ProfilePictureNotFoundException (ProfilePictureNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StudentNotAssociatedWithResponsibleAdultException.class)
    public final ResponseEntity<StandardError> StudentNotAssociatedWithResponsibleAdultException (StudentNotAssociatedWithResponsibleAdultException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public final ResponseEntity<StandardError> DuplicateResourceException (DuplicateResourceException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CannotTransferStudentToSameResponsibleAdultException.class)
    public final ResponseEntity<StandardError> CannotTransferStudentToSameResponsibleAdultException (CannotTransferStudentToSameResponsibleAdultException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InactiveAccountModificationException.class)
    public final ResponseEntity<StandardError> InactiveAccountModificationException (InactiveAccountModificationException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public final ResponseEntity<StandardError> IllegalStateException (IllegalStateException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public final ResponseEntity<StandardError> IllegalArgumentException (IllegalArgumentException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PermissionNotFoundException.class)
    public final ResponseEntity<StandardError> PermissionNotFoundException (PermissionNotFoundException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnderageResponsibleAdultException.class)
    public final ResponseEntity<StandardError> UnderageResponsibleAdultException (UnderageResponsibleAdultException ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StepUpRequiredException.class)
    public final ResponseEntity<StandardError> StepUpRequiredException (StepUpRequiredException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.PRECONDITION_REQUIRED);
    }

    @ExceptionHandler(InvalidBootstrapSecretException.class)
    public final ResponseEntity<StandardError> InvalidBootstrapSecretException (InvalidBootstrapSecretException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RateLimitExceededException .class)
    public final ResponseEntity<StandardError> RateLimitExceededException  (RateLimitExceededException   ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public final ResponseEntity<StandardError> RedisConnectionFailureException (RedisConnectionFailureException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(SensitiveOperationInternalError.class)
    public final ResponseEntity<StandardError> SensitiveOperationInternalError (SensitiveOperationInternalError  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RateLimitServiceUnavailableException .class)
    public final ResponseEntity<StandardError> RateLimitServiceUnavailableException  (RateLimitServiceUnavailableException   ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(StorageException.class)
    public final ResponseEntity<StandardError> StorageException (StorageException  ex, WebRequest webRequest) {
        return buildErrorCustomerResponse(ex, webRequest, HttpStatus.SERVICE_UNAVAILABLE);
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
