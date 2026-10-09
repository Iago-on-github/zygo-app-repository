package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.Shift;

import javax.validation.constraints.Future;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CustomerCalendarUpdateDTO(
        String description,
        Set<Shift> shifts
) {
}
