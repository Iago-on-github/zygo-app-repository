package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record UpdateEntityStatusDTO(@NotNull GeneralStatus status) {
}
