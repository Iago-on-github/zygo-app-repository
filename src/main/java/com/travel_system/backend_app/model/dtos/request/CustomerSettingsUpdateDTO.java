package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.Shift;

import java.time.DayOfWeek;
import java.util.Set;

public record CustomerSettingsUpdateDTO(
        Set<Shift> shifts,
        Set<DayOfWeek> operatingDays
) {
}
