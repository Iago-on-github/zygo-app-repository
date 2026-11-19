package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.enums.RegistrationGraceReason;

import java.time.LocalDate;

public record ClosureSuspension(RegistrationGraceReason reason, LocalDate deadline) {
}
