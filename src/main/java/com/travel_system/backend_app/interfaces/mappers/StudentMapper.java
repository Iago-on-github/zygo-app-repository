package com.travel_system.backend_app.interfaces.mappers;

import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.dtos.request.StudentUpdateDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    @Mapping(target = "password", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void studentUpdateFromDTO(StudentUpdateDTO studentUpdateDTO, @MappingTarget Student studentEntity);
}
