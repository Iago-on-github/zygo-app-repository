package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.dtos.request.CustomerOperationDataRequestDTO;
import com.travel_system.backend_app.model.enums.Shift;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

public record CustomerSettingsResponseDTO(
        Set<Shift> shifts,
        Set<DayOfWeek> operatingDays,
        String timeZone,
        CustomerOperationDataResponseDTO customerOperationDataResponse
) {
}
