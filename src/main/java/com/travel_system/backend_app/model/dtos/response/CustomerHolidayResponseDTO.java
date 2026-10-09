package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.Shift;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CustomerHolidayResponseDTO(
        UUID id,
        LocalDate holidayDate,
        String description,
        UUID createdBy,
        Set<Shift> shifts

) {
}
