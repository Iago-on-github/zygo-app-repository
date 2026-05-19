package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.GeneralStatus;

public record UpdateAdministratorStatusDTO(GeneralStatus status) {
}
