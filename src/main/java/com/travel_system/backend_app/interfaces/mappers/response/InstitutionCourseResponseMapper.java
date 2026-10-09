package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.InstitutionCourse;
import com.travel_system.backend_app.model.dtos.request.InstitutionCourseResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring")
public interface InstitutionCourseResponseMapper {

    @Mapping(target = "courseName", source = "name")
    InstitutionCourseResponseDTO toDTO(InstitutionCourse course);

    List<InstitutionCourseResponseDTO> toDTOList(Collection<InstitutionCourse> courses);
}