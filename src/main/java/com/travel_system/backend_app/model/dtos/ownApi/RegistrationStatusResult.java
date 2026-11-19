package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.enums.EffectiveRegistrationStatus;
import com.travel_system.backend_app.model.enums.RegistrationGraceReason;

import java.time.LocalDate;

public record RegistrationStatusResult(
        EffectiveRegistrationStatus status,
        RegistrationGraceReason graceReason,
        LocalDate deadline,
        RegistrationDeadlines deadlines,
        boolean windowOpen,
        boolean renewalInProgress
) {
    public static RegistrationStatusResult of(EffectiveRegistrationStatus status) {
        return new RegistrationStatusResult(status, null, null, null, false, false);
    }

    public boolean allowsAccess() {
        return status.allowsAccess();
    }
}
