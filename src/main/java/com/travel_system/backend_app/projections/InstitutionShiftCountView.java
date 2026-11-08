package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.Shift;

import java.util.List;
import java.util.UUID;

public interface InstitutionShiftCountView {
    UUID getInstitutionId();
    String getInstitutionName();
    Shift getShifts();
    long getTotal();
}
