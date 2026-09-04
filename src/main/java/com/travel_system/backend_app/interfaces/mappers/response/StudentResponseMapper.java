package com.travel_system.backend_app.interfaces.mappers.response;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.response.StudentResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudentResponseMapper {

    @Mapping(target = "email", source = "student.userAccount.email")
    StudentResponseDTO toDTO(Student student);
}
