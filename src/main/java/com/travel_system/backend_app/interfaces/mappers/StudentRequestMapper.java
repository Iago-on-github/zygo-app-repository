package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.StudentRequestDTO;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StudentRequestMapper {

    @Mapping(target = "userAccount.email", source = "email")
    @Mapping(target = "userAccount.password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void studentUpdateFromDTO(StudentUpdateDTO studentUpdateDTO, @MappingTarget Student student);

    @Mapping(target = "userAccount.password", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "studentShift", ignore = true)
    Student toEntity(StudentRequestDTO requestDTO);
}
