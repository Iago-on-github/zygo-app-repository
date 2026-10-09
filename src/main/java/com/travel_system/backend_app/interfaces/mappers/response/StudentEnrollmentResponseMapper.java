package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.StudentEnrollment;
import com.travel_system.backend_app.model.dtos.response.StudentEnrollmentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudentEnrollmentResponseMapper {

    @Mapping(target = "enrollmentId", source = "id")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "courseName", source = "course.name")
    @Mapping(target = "institutionId", source = "course.institution.id")
    @Mapping(target = "institutionName", source = "course.institution.institutionName")
    StudentEnrollmentResponseDTO toDTO(StudentEnrollment enrollment);
}
