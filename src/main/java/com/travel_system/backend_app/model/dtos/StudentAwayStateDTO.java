package com.travel_system.backend_app.model.dtos;

import com.travel_system.backend_app.model.enums.StudentTravelStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentAwayStateDTO(UUID studentTravelId,
                                  UUID studentId,
                                  String studentEmail,
                                  StudentTravelStatus studentTravelStatus,
                                  boolean embark) {
}
