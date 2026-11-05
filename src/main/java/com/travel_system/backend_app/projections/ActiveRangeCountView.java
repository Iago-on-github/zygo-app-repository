package com.travel_system.backend_app.projections;

public interface ActiveRangeCountView {
    Long getActiveStudents();
    Long getActiveDrivers();
    Long getGraceStudents();
    Long getGraceDrivers();
    Long getAwaitingClosure();
}
