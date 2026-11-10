package com.travel_system.backend_app.annotations.validators;

import com.travel_system.backend_app.annotations.ValidTimeZone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;

public class ValidTimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // se null dexa o @notnull tratar
        }

        Set<String> availableZoneIds = ZoneId.getAvailableZoneIds();

        // só aceita identificadores de região conhecidas pelo java (como America/Manaus)
        // recusa valores fixos de forma automática, jaq o getAvailableZone nao retorna eses valores

        return availableZoneIds.stream().anyMatch(zone -> zone.equals(value));
    }
}
