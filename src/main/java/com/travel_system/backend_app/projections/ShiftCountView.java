package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.enums.Shift;

import java.util.List;

public interface ShiftCountView {
    Shift getShifts();
    long getTotal();
}
