package com.travel_system.backend_app.projections;

import com.travel_system.backend_app.model.dtos.request.InstitutionCourseResponseDTO;
import com.travel_system.backend_app.model.enums.InstitutionType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface InstitutionCourseCountView {
    UUID getInstitutionId();
    String getInstitutionName();
    InstitutionType getInstitutionType();
    UUID getCourseId();
    String getCourseName();
    long getTotal();
}