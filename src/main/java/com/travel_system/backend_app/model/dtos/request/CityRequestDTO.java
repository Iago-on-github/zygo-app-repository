package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;

import javax.validation.constraints.NotNull;

public record CityRequestDTO(
        @NotNull
        String name,
        @NotNull
        CitySize size
) {
}
