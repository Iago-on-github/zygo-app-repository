package com.travel_system.backend_app.model.dtos.ownApi;

/*
* leva os dados do escopo de bloqueio
* */

import com.travel_system.backend_app.model.enums.RestrictionCode;
import com.travel_system.backend_app.model.enums.RestrictionLevel;
import com.travel_system.backend_app.model.enums.RestrictionPhase;

import java.time.LocalDate;

public record AccessRestriction(
        RestrictionCode code,
        RestrictionLevel level,
        RestrictionPhase phase,
        String message,
        LocalDate deadline // anulável

) {
}
