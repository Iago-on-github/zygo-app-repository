package com.travel_system.backend_app.projections;

import java.time.LocalDate;

public interface TermEndCountView {
    LocalDate getEndTerm();
    Long getTotal();
}
