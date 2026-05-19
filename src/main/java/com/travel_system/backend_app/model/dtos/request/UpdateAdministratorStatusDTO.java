package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

import javax.validation.constraints.NotBlank;

public record UpdateAdministratorStatusDTO(@NotBlank GeneralStatus status) {
}
