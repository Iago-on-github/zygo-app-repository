package com.travel_system.backend_app.annotations;

import com.travel_system.backend_app.annotations.validators.ValidTimeZoneValidator;

import javax.validation.Constraint;
import java.lang.annotation.*;

@Constraint(validatedBy = ValidTimeZoneValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidTimeZone {
    String message() default "TimeZone inválido. Selecione um tipo válido, ex.: America/Sao_Paulo";
}
