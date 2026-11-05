package com.travel_system.backend_app.projections;

public interface RenewalProgressView {
    Long getNotStarted();
    Long getInDraft();
    Long getUnderReview();
}
